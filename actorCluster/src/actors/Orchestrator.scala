package actors

/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.MqHost
import types.OpaqueTypes.MqPassword
import types.OpaqueTypes.MqPort
import types.OpaqueTypes.MqUser
import types.OpaqueTypes.QueueName
import types.OpaqueTypes.RemoteStorageHost
import types.OpaqueTypes.RemoteStoragePassword
import types.OpaqueTypes.RemoteStoragePort
import types.OpaqueTypes.RemoteStorageUser
import types.OpaqueTypes.RoutingKey
import types.Task

import scala.concurrent.duration.*

import api.ApiManager
import execution.ExecutionManager
import files.RemoteFileManager
import mq.MqManager

private val DefaultExchange = ExchangeName("processing-exchange")
private val DefaultQueue = QueueName("processing-queue")
private val DefaultRoutingKey = RoutingKey("processing")
val DefaultProcessorQuantity = 1

object Orchestrator:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class SetProcessorLimit(limit: Int) extends Command:
    require(limit > 0, "Processor limit must be greater than 0")
  end SetProcessorLimit
  final case class ProcessTask(task: Task) extends Command
  final case class RegisterLog(task: Task, log: String) extends Command

  // Internal command protocol
  final case class GeneralAcknowledgeTask(task: Task) extends Command
  final case class GeneralRejectTask(task: Task) extends Command
  case object Shutdown extends Command

  private type CommandOrResponse = Command | ExecutionManager.Response |
    RemoteFileManager.Response // | MqManager.Response

  implicit val timeout: Timeout = Timeout(10.seconds)

  def apply(): Behavior[CommandOrResponse] = orchestrating()

  def orchestrating(
      activeWorkers: Int = 0
  ): Behavior[CommandOrResponse] =
    Behaviors
      .setup[CommandOrResponse] { context =>
        context.log.info("Orchestrator started...")

        val executionManager = context.spawn(
          ExecutionManager(context.self),
          "processing-manager"
        )

        val remoteFileManager = context.spawn(
          RemoteFileManager(
            RemoteStorageHost("localhost"),
            RemoteStoragePort(21),
            RemoteStorageUser("one"),
            RemoteStoragePassword("123"),
            context.self
          ),
          "ftp-manager"
        )

        val mqManager = context.spawn(
          MqManager(
            MqHost("localhost"),
            MqPort(5672),
            MqUser("guest"),
            MqPassword("guest"),
            DefaultQueue,
            DefaultProcessorQuantity,
            context.self
          ),
          "mq-manager"
        )

        val apiManager = context.spawn(
          ApiManager(),
          "api-manager"
        )

        val systemMonitor = context.spawn(
          SystemMonitor(DefaultProcessorQuantity, context.self),
          "system-monitor"
        )

        def orchestrating(activeWorkers: Int): Behavior[CommandOrResponse] =
          Behaviors
            .receiveMessage[CommandOrResponse] { message =>
              message match

                /* **********************************************************************
                 * Public commands
                 * ********************************************************************** */

                case SetProcessorLimit(limit) =>
                  context.log.info(
                    s"SetProcessorLimit command received. Limit --> $limit"
                  )
                  mqManager ! MqManager.MqSetQosPrefetchCount(limit)
                  Behaviors.same

                case ProcessTask(task) =>
                  context.log.info(
                    s"ProcessTask command received. Task --> $task"
                  )
                  remoteFileManager ! RemoteFileManager.DownloadTaskFiles(task)
                  context.self ! RegisterLog(
                    task,
                    "Task received for processing."
                  )

                  val numActiveWorkers = activeWorkers + 1
                  systemMonitor ! SystemMonitor.NotifyActiveProcessors(
                    numActiveWorkers
                  )
                  orchestrating(numActiveWorkers)

                case GeneralAcknowledgeTask(task) =>
                  context.log.info(
                    s"GeneralAcknowledgeTask command received. Task --> $task"
                  )
                  mqManager ! MqManager.MqAcknowledgeTask(task.mqId)

                  context.self ! RegisterLog(
                    task,
                    "Task ack sent to broker."
                  )

                  val numActiveWorkers = activeWorkers - 1
                  systemMonitor ! SystemMonitor.NotifyActiveProcessors(
                    numActiveWorkers
                  )
                  orchestrating(numActiveWorkers)

                case GeneralRejectTask(task) =>
                  context.log.info(
                    s"GeneralRejectTask command received. Task --> $task"
                  )
                  mqManager ! MqManager.MqRejectTask(task.mqId)

                  context.self ! RegisterLog(
                    task,
                    "Task reject sent to broker."
                  )

                  val numActiveWorkers = activeWorkers - 1
                  systemMonitor ! SystemMonitor.NotifyActiveProcessors(
                    numActiveWorkers
                  )
                  orchestrating(numActiveWorkers)

                case RegisterLog(task, log) =>
                  context.log.info(
                    s"RegisterLog command received. Log --> $log"
                  )
                  apiManager ! ApiManager.ApiTaskLog(
                    task.copy(logMessage = Some(log))
                  )
                  Behaviors.same

                /* **********************************************************************
                 * Responses from other actors
                 * ********************************************************************** */

                case RemoteFileManager.TaskDownloaded(task) =>
                  context.log.info(
                    s"TaskDownloaded response received. Task --> $task."
                  )
                  executionManager ! ExecutionManager.ExecuteTask(task)

                  context.self ! RegisterLog(
                    task,
                    "Task files donwloaded for processing."
                  )

                  Behaviors.same

                case ExecutionManager.TaskExecuted(task) =>
                  context.log.info(
                    s"TaskExecuted response received. Task --> $task"
                  )
                  remoteFileManager ! RemoteFileManager.UploadTaskFiles(task)

                  context.self ! RegisterLog(task, "Task processing completed.")

                  Behaviors.same

                case RemoteFileManager.TaskUploaded(task) =>
                  context.log.info(
                    s"TaskUploaded response received. Task --> $task"
                  )

                  context.self ! RegisterLog(task, "Task files uploaded.")

                  val taskForNextStage = task.copy(
                    containerImagesPaths = task.containerImagesPaths.tail,
                    processingStages = task.processingStages.tail
                  )

                  if !taskForNextStage.processingStages.isEmpty then
                    mqManager ! MqManager.MqSendMessage(
                      taskForNextStage,
                      DefaultExchange,
                      DefaultRoutingKey
                    )

                    context.self ! RegisterLog(
                      taskForNextStage,
                      "Task sent for next processing stage."
                    )
                  end if

                  context.self ! GeneralAcknowledgeTask(task)
                  Behaviors.same

                case RemoteFileManager.TaskDownloadFailed(task) =>
                  context.log.info(
                    s"TaskDownloadFailed response received. Task --> $task"
                  )

                  context.self ! RegisterLog(
                    task,
                    s"Task files download failed with message: ${task.logMessage}"
                  )

                  context.self ! GeneralRejectTask(task)
                  Behaviors.same

                case RemoteFileManager.TaskUploadFailed(task) =>
                  context.log.info(
                    s"TaskUploadFailed response received. Task --> $task"
                  )

                  context.self ! RegisterLog(
                    task,
                    s"Task files upload failed with message: ${task.logMessage}"
                  )

                  context.self ! GeneralRejectTask(task)
                  Behaviors.same

                case ExecutionManager.TaskExecutionError(task) =>
                  context.log.info(
                    s"TaskExecutionError response received. Task --> $task"
                  )

                  context.self ! RegisterLog(
                    task,
                    s"Task execution failed with message: ${task.logMessage}"
                  )

                  context.self ! GeneralRejectTask(task)
                  Behaviors.same

                case Shutdown =>
                  context.log.info("Shutdown command received.")
                  mqManager ! MqManager.Shutdown
                  executionManager ! ExecutionManager.Shutdown
                  remoteFileManager ! RemoteFileManager.Shutdown
                  apiManager ! ApiManager.Shutdown
                  systemMonitor ! SystemMonitor.Shutdown
                  Behaviors.stopped
            }
        end orchestrating

        orchestrating(activeWorkers)
      }
      .narrow
end Orchestrator

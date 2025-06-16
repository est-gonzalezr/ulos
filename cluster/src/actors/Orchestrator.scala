package actors

/** @author
  *   Esteban Gonzalez Ruales
  */

import scala.concurrent.duration.*

import actors.execution.ExecutionManager
import actors.files.RemoteFileManager
import actors.mq.MqManager
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
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
  final case class GracefulShutdown(reason: String) extends Command
  final case class Fail(reason: String) extends Command

  private type CommandOrResponse =
    Command | ExecutionManager.Response |
      RemoteFileManager.Response // | MqManager.Response

  implicit val timeout: Timeout = Timeout(10.seconds)

  def apply(
      mqHost: MqHost,
      mqPort: MqPort,
      mqUser: MqUser,
      mqPassword: MqPassword,
      mqExchangeName: ExchangeName,
      mqQueueName: QueueName,
      remoteStorageHost: RemoteStorageHost,
      remoteStoragePort: RemoteStoragePort,
      remoteStorageUser: RemoteStorageUser,
      remoteStoragePassword: RemoteStoragePassword
  ): Behavior[CommandOrResponse] = orchestrating(
    mqHost,
    mqPort,
    mqUser,
    mqPassword,
    mqExchangeName,
    mqQueueName,
    remoteStorageHost,
    remoteStoragePort,
    remoteStorageUser,
    remoteStoragePassword
  )

  def orchestrating(
      mqHost: MqHost,
      mqPort: MqPort,
      mqUser: MqUser,
      mqPassword: MqPassword,
      mqExchangeName: ExchangeName,
      mqQueueName: QueueName,
      remoteStorageHost: RemoteStorageHost,
      remoteStoragePort: RemoteStoragePort,
      remoteStorageUser: RemoteStorageUser,
      remoteStoragePassword: RemoteStoragePassword,
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
            remoteStorageHost,
            remoteStoragePort,
            remoteStorageUser,
            remoteStoragePassword,
            context.self
          ),
          "ftp-manager"
        )

        val mqManager = context.spawn(
          MqManager(
            mqHost,
            mqPort,
            mqUser,
            mqPassword,
            mqQueueName,
            DefaultProcessorQuantity,
            context.self
          ),
          "mq-manager"
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
                    s"GeneralAcknowledgeTask command received. TaskId --> ${task.mqId}."
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
                    s"GeneralRejectTask command received. TaskId --> ${task.mqId}."
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
                    s"RegisterLog command received. Log --> $log."
                  )
                  Behaviors.same

                /* **********************************************************************
                 * Responses from other actors
                 * ********************************************************************** */

                case RemoteFileManager.TaskDownloaded(task) =>
                  context.log.info(
                    s"TaskDownloaded response received. TaskId --> ${task.mqId}, Files --> ${task.filePath.toString}."
                  )
                  executionManager ! ExecutionManager.ExecuteTask(task)

                  context.self ! RegisterLog(
                    task,
                    "Task files donwloaded for processing."
                  )

                  Behaviors.same

                case ExecutionManager.TaskExecuted(task) =>
                  context.log.info(
                    s"TaskExecuted response received. TaskId --> ${task.taskId}"
                  )
                  remoteFileManager ! RemoteFileManager.UploadTaskFiles(task)

                  context.self ! RegisterLog(task, "Task processing completed.")

                  Behaviors.same

                case RemoteFileManager.TaskUploaded(task) =>
                  context.log.info(
                    s"TaskUploaded response received. TaskId --> ${task.mqId}, Files --> ${task.filePath.toString}."
                  )

                  context.self ! RegisterLog(task, "Task files uploaded.")

                  if task.routingKeys.tail.nonEmpty then
                    val taskForNextStage = task.copy(
                      routingKeys = task.routingKeys.tail
                    )

                    mqManager ! MqManager.MqSendMessage(
                      taskForNextStage,
                      mqExchangeName,
                      RoutingKey(taskForNextStage.routingKeys.head)
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
                    s"TaskDownloadFailed response received. TaskId --> ${task.taskId}."
                  )

                  context.self ! RegisterLog(
                    task,
                    s"Task files download failed with message: ${task.logMessage}"
                  )

                  context.self ! GeneralRejectTask(task)
                  Behaviors.same

                case RemoteFileManager.TaskUploadFailed(task) =>
                  context.log.info(
                    s"TaskUploadFailed response received. TaskId --> ${task.taskId}, Files --> ${task.filePath.toString}."
                  )

                  context.self ! RegisterLog(
                    task,
                    s"Task files upload failed with message: ${task.logMessage}"
                  )

                  context.self ! GeneralRejectTask(task)
                  Behaviors.same

                case ExecutionManager.TaskExecutionError(task) =>
                  context.log.info(
                    s"TaskExecutionError response received. TaskId --> ${task.taskId}."
                  )

                  context.self ! RegisterLog(
                    task,
                    s"Task execution failed with message: ${task.logMessage}"
                  )

                  context.self ! GeneralRejectTask(task)
                  Behaviors.same

                case GracefulShutdown(reason) =>
                  context.log.info(
                    s"GracefulShutdown command received. Reason --> $reason"
                  )
                  mqManager ! MqManager.GracefulShutdown
                  Behaviors.stopped

                case Fail(reason) =>
                  context.log.error(
                    s"Fail command received. Reason --> $reason"
                  )
                  Behaviors.stopped
            }
        end orchestrating

        orchestrating(activeWorkers)
      }
      .narrow
end Orchestrator

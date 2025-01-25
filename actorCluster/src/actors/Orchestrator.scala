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

  // Internal command protocol
  final case class GeneralAcknowledgeTask(task: Task) extends Command
  final case class GeneralRejectTask(task: Task) extends Command

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
                  mqManager ! MqManager.MqSetQosPrefetchCount(limit)
                  Behaviors.same

                /* ProcessTask
                 *
                 * Process a task.
                 */
                case ProcessTask(task) =>
                  remoteFileManager ! RemoteFileManager.DownloadTaskFiles(task)
                  val numActiveWorkers = activeWorkers + 1
                  systemMonitor ! SystemMonitor.NotifyActiveProcessors(
                    numActiveWorkers
                  )
                  orchestrating(numActiveWorkers)

                case GeneralAcknowledgeTask(task) =>
                  mqManager ! MqManager.MqAcknowledgeTask(task.mqId)
                  val numActiveWorkers = activeWorkers - 1
                  systemMonitor ! SystemMonitor.NotifyActiveProcessors(
                    numActiveWorkers
                  )
                  orchestrating(numActiveWorkers)

                case GeneralRejectTask(task) =>
                  mqManager ! MqManager.MqRejectTask(task.mqId)
                  val numActiveWorkers = activeWorkers - 1
                  systemMonitor ! SystemMonitor.NotifyActiveProcessors(
                    numActiveWorkers
                  )
                  orchestrating(numActiveWorkers)

                /* **********************************************************************
                 * Responses from other actors
                 * ********************************************************************** */

                case RemoteFileManager.TaskDownloaded(task, path) =>
                  executionManager ! ExecutionManager.ExecuteTask(task, path)
                  Behaviors.same

                case ExecutionManager.TaskExecuted(task) =>
                  remoteFileManager ! RemoteFileManager.UploadTaskFiles(task)
                  Behaviors.same

                case RemoteFileManager.TaskUploaded(task) =>
                  if !task.processingStages.isEmpty then
                    mqManager ! MqManager.MqSendMessage(
                      task,
                      DefaultExchange,
                      DefaultRoutingKey
                    )
                  end if

                  context.self ! GeneralAcknowledgeTask(task)
                  Behaviors.same

                case RemoteFileManager.TaskDownloadFailed(task) =>
                  context.self ! GeneralRejectTask(task)
                  Behaviors.same

                case RemoteFileManager.TaskUploadFailed(task) =>
                  context.self ! GeneralRejectTask(task)
                  Behaviors.same

                case ExecutionManager.TaskExecutionError(task) =>
                  context.self ! GeneralRejectTask(task)
                  Behaviors.same

            }
        end orchestrating

        orchestrating(activeWorkers)
      }
      .narrow
end Orchestrator

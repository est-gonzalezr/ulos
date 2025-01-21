package actors

import akka.actor.ProviderSelection.Remote
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import types.MqMessage
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
import types.Task

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

import execution.ExecutionManager
import files.RemoteFileManager
import mq.MqManager
import types.OpaqueTypes.RoutingKey

private val DefaultProcessors = 5
private val DefaultExchange = ExchangeName("processing-exchange")
private val DefaultQueue = QueueName("processing-queue")
private val DefaultRoutingKey = RoutingKey("processing")

object Orchestrator:
  // Command protocol
  sealed trait Command

  // Public command protocol
  case object IncreaseProcessors extends Command
  case object DecreaseProcessors extends Command
  final case class ProcessTask(task: Task) extends Command

  private type CommandOrResponse = Command | ExecutionManager.Response |
    RemoteFileManager.Response // | MqManager.Response

  implicit val timeout: Timeout = Timeout(10.seconds)

  def apply(): Behavior[CommandOrResponse] = orchestrating()

  def orchestrating(
      workerNumber: Int = DefaultProcessors
  ): Behavior[CommandOrResponse] =
    Behaviors
      .setup[CommandOrResponse] { context =>
        context.log.info("Orchestrator started...")

        val executionManager = context.spawn(
          ExecutionManager(DefaultProcessors, context.self),
          "processing-manager"
        )

        val remoteFileManager = context.spawn(
          RemoteFileManager(
            RemoteStorageHost("localhost"),
            RemoteStoragePort(21),
            RemoteStorageUser("one"),
            RemoteStoragePassword("123"),
            DefaultProcessors,
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
            context.self
          ),
          "mq-manager"
        )

        Behaviors
          .receiveMessage[CommandOrResponse] { message =>
            message match

              /* **********************************************************************
               * Public commands
               * ********************************************************************** */

              /* IncreaseProcessors
               *
               * Increase the number of processors.
               */
              case IncreaseProcessors =>
                orchestrating(workerNumber + 1)

              /* DecreaseProcessors
               *
               * Decrease the number of processors.
               */
              case DecreaseProcessors =>
                orchestrating(workerNumber - 1)

              /* ProcessTask
               *
               * Process a task.
               */
              case ProcessTask(task) =>
                remoteFileManager ! RemoteFileManager.DownloadTaskFiles(task)
                Behaviors.same

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
                mqManager ! MqManager.MqAcknowledgeTask(task.mqId)
                mqManager ! MqManager.MqSendMessage(
                  task,
                  DefaultExchange,
                  DefaultRoutingKey
                )
                Behaviors.same

              case RemoteFileManager.TaskDownloadFailed(task) =>
                mqManager ! MqManager.MqRejectTask(task.mqId)
                Behaviors.same

              case RemoteFileManager.TaskUploadFailed(task) =>
                mqManager ! MqManager.MqRejectTask(task.mqId)
                Behaviors.same

              case ExecutionManager.TaskExecutionError(task) =>
                mqManager ! MqManager.MqRejectTask(task.mqId)
                Behaviors.same

          }
      }
      .narrow
end Orchestrator

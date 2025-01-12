package actors

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
import types.Task

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

import execution.ExecutionManager
import files.RemoteFileManager
import mq.MqManager

private val DefaultProcessors = 5
private val DefaultExchange = ExchangeName("processing-exchange")

object Orchestrator:
  // Command protocol
  sealed trait Command

  // Public command protocol
  case object IncreaseProcessors extends Command
  case object DecreaseProcessors extends Command
  final case class ProcessTask(task: Task) extends Command

  private type CommandOrResponse = Command | ExecutionManager.Response |
    RemoteFileManager.Response

  implicit val timeout: Timeout = Timeout(10.seconds)

  def apply(): Behavior[CommandOrResponse] = orchestrating()

  def orchestrating(
      defaultWorkers: Int = DefaultProcessors
  ): Behavior[CommandOrResponse] =
    Behaviors
      .setup[CommandOrResponse] { context =>
        context.log.info("Orchestrator started...")

        val executionManager =
          context.spawn(
            ExecutionManager(DefaultProcessors, context.self),
            "processing-manager"
          )

        val remoteFileManager =
          context.spawn(
            RemoteFileManager(DefaultProcessors, context.self),
            "ftp-manager"
          )

        val mqManager = context.spawn(
          MqManager(
            context.self,
            MqHost("localhost"),
            MqPort(5672),
            MqUser("guest"),
            MqPassword("guest"),
            QueueName("test")
          ),
          "mq-manager"
        )

        Behaviors
          .receiveMessage[CommandOrResponse] { message =>
            message match
              // Messages from system monitor
              case IncreaseProcessors =>
                val newWorkerQuantity = defaultWorkers + 1
                executionManager ! ExecutionManager.SetMaxExecutionWorkers(
                  newWorkerQuantity
                )
                remoteFileManager ! RemoteFileManager.SetMaxRemoteFileWorkers(
                  newWorkerQuantity
                )

                orchestrating(newWorkerQuantity)

              case DecreaseProcessors =>
                val newWorkerQuantity = defaultWorkers - 1
                executionManager ! ExecutionManager.SetMaxExecutionWorkers(
                  newWorkerQuantity
                )
                remoteFileManager ! RemoteFileManager.SetMaxRemoteFileWorkers(
                  newWorkerQuantity
                )

                orchestrating(newWorkerQuantity)

              case ProcessTask(task) =>
                remoteFileManager ! RemoteFileManager.DownloadTaskFiles(task)
                Behaviors.same

              case RemoteFileManager.TaskDownloaded(task, path) =>
                executionManager ! ExecutionManager.ExecuteTask(task, path)
                Behaviors.same

              case ExecutionManager.TaskExecuted(task) =>
                task.mqId match
                  case Some(id) =>
                    mqManager ! MqManager.MqAcknowledgeTask(id)
                  // mqManager ! MqManager.MqSendMessage
                  case None => context.log.error("task should have id")
                end match

                Behaviors.same

              case _ => Behaviors.same
          }
      }
      .narrow
end Orchestrator

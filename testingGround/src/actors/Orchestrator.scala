package actors

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.{Success, Failure}

import execution.ExecutionManager
import files.RemoteFileManager
import mq.MqManager

import types.MqMessage
import types.Task

private val DefaultProcessors = 5

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

        val remoteFilesManager =
          context.spawn(
            RemoteFileManager(DefaultProcessors, context.self),
            "ftp-manager"
          )

        val mqManager = context.spawn(MqManager(context.self), "mq-manager")

        Behaviors
          .receiveMessage[CommandOrResponse] { message =>
            message match
              // Messages from system monitor
              case IncreaseProcessors =>
                val newWorkerQuantity = defaultWorkers + 1
                executionManager ! ExecutionManager.SetMaxExecutionWorkers(
                  newWorkerQuantity
                )
                remoteFilesManager ! RemoteFileManager.SetMaxRemoteFileWorkers(
                  newWorkerQuantity
                )

                orchestrating(newWorkerQuantity)

              case DecreaseProcessors =>
                val newWorkerQuantity = defaultWorkers - 1
                executionManager ! ExecutionManager.SetMaxExecutionWorkers(
                  newWorkerQuantity
                )
                remoteFilesManager ! RemoteFileManager.SetMaxRemoteFileWorkers(
                  newWorkerQuantity
                )

                orchestrating(newWorkerQuantity)

          }
      }
      .narrow
end Orchestrator

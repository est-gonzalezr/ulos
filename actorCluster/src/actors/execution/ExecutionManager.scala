package actors.execution

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import os.Path
import types.Task

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

// here it is necesaary to have a max and active processor count since
// if we need to decrease the number of processors and all are active,
// then the command will fail since decreasing the number of processors would be 0

// TODO: implement caching of messages that are not processed due to lack of processors

object ExecutionManager:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ExecuteTask(task: Task, path: Path) extends Command

  // Internal command protocol
  final case class ReportTaskExecuted(task: Task) extends Command
  final case class ReportTaskFailed(task: Task) extends Command

  // Response protocol
  sealed trait Response
  final case class TaskExecuted(task: Task) extends Response
  final case class TaskExecutionError(task: Task) extends Response

  implicit val timeout: Timeout = 10.seconds

  def apply(
      maxWorkers: Int,
      replyTo: ActorRef[Response]
  ): Behavior[Command] =
    setup(0, maxWorkers, replyTo)
  end apply

  def setup(
      activeWorkers: Int,
      maxWorkers: Int,
      replyTo: ActorRef[Response]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("ExecutionManager started...")

      Behaviors.receiveMessage { message =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          /* ExecuteTask
           *
           * Execute a task.
           */
          case ExecuteTask(task, path) =>
            context.log.info(s"ExecuteTask command received. Task --> $task.")

            context.log.info(s"Spawning execution worker...")
            val executionWorker =
              context.spawnAnonymous(
                ExecutionWorker()
              )

            context.log.info(s"Execution worker spawned.")
            context.log.info(
              s"Sending task to execution worker. Task --> $task."
            )

            context.askWithStatus[
              ExecutionWorker.ExecuteTask,
              Task
            ](
              executionWorker,
              replyTo => ExecutionWorker.ExecuteTask(task, path, replyTo)
            ) {
              case Success(task) =>
                context.log.info(
                  s"Task execution success. Task awaiting rerouting to orchestrator. Task --> $task."
                )
                ReportTaskExecuted(task)
              case Failure(throwable) =>
                context.log.error(
                  s"Task execution failure. Task awaiting rejection to MQ. Task --> $task."
                )
                ReportTaskFailed(
                  task.copy(errorMessage = Some(throwable.getMessage))
                )
            }

            Behaviors.same

          /* **********************************************************************
           * Internal commands
           * ********************************************************************** */

          case ReportTaskExecuted(task) =>
            context.log.info(
              s"ReportTaskExecuted command received. Task --> $task."
            )
            context.log.info(
              s"Sending task to orchestrator... Task --> $task."
            )

            replyTo ! TaskExecuted(task)
            Behaviors.same

          case ReportTaskFailed(task) =>
            context.log.info(
              s"ReportTaskFailed command received. Task --> $task."
            )
            context.log.info(
              s"Sending task to orchestrator... Task --> $task."
            )
            replyTo ! TaskExecutionError(task)
            Behaviors.same
      }
    }
  end setup
end ExecutionManager

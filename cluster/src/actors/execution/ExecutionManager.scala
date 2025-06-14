package actors.execution

/**
 * @author
 *   Esteban Gonzalez Ruales
 */

import scala.util.Failure
import scala.util.Success

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import types.Task

object ExecutionManager:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ExecuteTask(task: Task) extends Command

  // Internal command protocol
  final case class ReportTaskExecuted(task: Task) extends Command
  final case class ReportTaskFailed(task: Task) extends Command

  // Response protocol
  sealed trait Response
  final case class TaskExecuted(task: Task) extends Response
  final case class TaskExecutionError(task: Task) extends Response

  // implicit val timeout: Timeout = 300.seconds

  def apply(
    replyTo: ActorRef[Response],
  ): Behavior[Command] =
    setup(replyTo)
  end apply

  def setup(
    replyTo: ActorRef[Response],
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("ExecutionManager started...")

      Behaviors.receiveMessage { message =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          case ExecuteTask(task) =>
            context.log.info(s"ExecuteTask command received. Task --> $task.")

            context.log.info(s"Spawning execution worker...")
            val executionWorker =
              context.spawnAnonymous(
                ExecutionWorker(),
              )

            context.log.info(s"Execution worker spawned.")
            context.log.info(
              s"Sending task to execution worker. Task --> $task.",
            )

            context.askWithStatus[
              ExecutionWorker.ExecuteTask,
              Task,
            ](
              executionWorker,
              replyTo => ExecutionWorker.ExecuteTask(task, replyTo),
            ) {
              case Success(task) =>
                context.log.info(
                  s"Task execution success. Task awaiting rerouting to orchestrator. Task --> $task.",
                )
                ReportTaskExecuted(task)
              case Failure(exception) =>
                context.log.error(
                  s"Task execution failure. Task awaiting rejection to MQ. Task --> $task.",
                )
                ReportTaskFailed(
                  task.copy(logMessage = Some(exception.getMessage)),
                )
            }(using task.timeout)

            Behaviors.same

          /* **********************************************************************
           * Internal commands
           * ********************************************************************** */

          case ReportTaskExecuted(task) =>
            context.log.info(
              s"ReportTaskExecuted command received. Task --> $task.",
            )
            context.log.info(
              s"Sending task to orchestrator... Task --> $task.",
            )

            replyTo ! TaskExecuted(task)
            Behaviors.same

          case ReportTaskFailed(task) =>
            context.log.info(
              s"ReportTaskFailed command received. Task --> $task.",
            )
            context.log.info(
              s"Sending task to orchestrator... Task --> $task.",
            )
            replyTo ! TaskExecutionError(task)
            Behaviors.same
      }
    }
  end setup
end ExecutionManager

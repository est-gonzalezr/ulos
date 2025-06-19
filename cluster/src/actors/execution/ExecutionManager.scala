package actors.execution

import scala.util.Failure
import scala.util.Success

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
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

  def apply(
      replyTo: ActorRef[Response]
  ): Behavior[Command] =
    setup(replyTo)
  end apply

  def setup(
      replyTo: ActorRef[Response]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("ExecutionManager started...")
      handleMessages(replyTo)
    }
  end setup

  def handleMessages(replyTo: ActorRef[Response]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case ExecuteTask(task) =>
          delegateTaskExecution(context, task)
          Behaviors.same

        /* **********************************************************************
         * Internal commands
         * ********************************************************************** */

        case ReportTaskExecuted(task) =>
          replyTo ! TaskExecuted(task)
          Behaviors.same

        case ReportTaskFailed(task) =>
          replyTo ! TaskExecutionError(task)
          Behaviors.same
    }
  end handleMessages

  private def delegateTaskExecution(
      context: ActorContext[Command],
      task: Task
  ): Unit =
    val executionWorker = context.spawnAnonymous(
      ExecutionWorker()
    )

    context.askWithStatus[
      ExecutionWorker.ExecuteTask,
      Done
    ](
      executionWorker,
      replyTo => ExecutionWorker.ExecuteTask(task, replyTo)
    ) {
      case Success(Done) =>
        ReportTaskExecuted(task)
      case Failure(exception) =>
        ReportTaskFailed(
          task.copy(logMessage = Some(exception.getMessage))
        )
    }(using task.timeout)
  end delegateTaskExecution
end ExecutionManager

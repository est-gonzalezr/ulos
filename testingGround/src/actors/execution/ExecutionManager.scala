package actors.execution

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
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
  case object IncreaseProcessors extends Command
  case object DecreaseProcessors extends Command
  final case class ExecuteTask(task: Task) extends Command

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
      ref: ActorRef[Response]
  ): Behavior[Command] =
    delegateProcessing(0, maxWorkers, ref)
  end apply

  def delegateProcessing(
      activeWorkers: Int,
      maxWorkers: Int,
      ref: ActorRef[Response]
  ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case IncreaseProcessors =>
          context.log.info(
            s"Increasing max processors from $maxWorkers to ${maxWorkers + 1}"
          )
          delegateProcessing(activeWorkers, maxWorkers + 1, ref)

        case DecreaseProcessors =>
          if maxWorkers > 1 then
            context.log.info(
              s"Decreasig max processors from $maxWorkers to ${maxWorkers - 1}"
            )
            delegateProcessing(activeWorkers, maxWorkers - 1, ref)
          else
            context.log.warn("Cannot decrease processors below 1")
            Behaviors.same

        case ExecuteTask(task) =>
          if activeWorkers < maxWorkers then
            context.log.info(
              s"Delegating task to executionWorker: ${task.taskType}"
            )

            val executionWorker =
              context.spawnAnonymous(
                ExecutionWorker()
              )

            context.askWithStatus[
              ExecutionWorker.ExecuteTask,
              Boolean
            ](
              executionWorker,
              ref => ExecutionWorker.ExecuteTask(task, ref)
            ) {
              case Success(passed) =>
                ReportTaskExecuted(task.copy())
              case Failure(throwable) =>
                ReportTaskFailed(
                  task.copy(errorMessage = Some(throwable.getMessage))
                )
            }

            delegateProcessing(activeWorkers + 1, maxWorkers, ref)
          else
            context.log.info("All processors are busy")
            Behaviors.same

        /* **********************************************************************
         * Internal commands
         * ********************************************************************** */

        case ReportTaskExecuted(task) =>
          ref ! TaskExecuted(task)
          delegateProcessing(activeWorkers - 1, maxWorkers, ref)

        case ReportTaskFailed(task) =>
          ref ! TaskExecutionError(task)
          delegateProcessing(activeWorkers - 1, maxWorkers, ref)
    }

  end delegateProcessing
end ExecutionManager

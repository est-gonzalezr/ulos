import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

// here it is necesaary to have a max and active processor count since
// if we need to decrease the number of processors and all are active,
// then the command will fail since decreasing the number of processors would be 0

object ExecutionManager:
  sealed trait Command
  case object IncreaseProcessors extends Command
  case object DecreaseProcessors extends Command
  final case class ExecuteTask(task: Task) extends Command
  final case class ReportExecutedTask(task: Task) extends Command
  final case class ReportFailedTask(task: Task) extends Command

  sealed trait Response
  final case class TaskExecuted(task: Task) extends Response
  final case class TaskFailed(task: Task) extends Response

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
              ExecutionWorker.TaskExecuted
            ](
              executionWorker,
              ref => ExecutionWorker.ExecuteTask(task, ref)
            ) {
              case Success(ExecutionWorker.TaskExecuted(task)) =>
                ReportExecutedTask(task)
              case Failure(throwable) =>
                ReportFailedTask(task)
            }

            delegateProcessing(activeWorkers + 1, maxWorkers, ref)
          else
            context.log.info("All processors are busy")
            Behaviors.same

        case ReportExecutedTask(task) =>
          ref ! TaskExecuted(task)
          delegateProcessing(activeWorkers - 1, maxWorkers, ref)

        case ReportFailedTask(task) =>
          ref ! TaskFailed(task)
          delegateProcessing(activeWorkers - 1, maxWorkers, ref)
    }

  end delegateProcessing
end ExecutionManager

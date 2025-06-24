package actors.execution

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.ChildFailed
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.Terminated
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import types.Task

object ExecutionManager:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ExecuteTask(task: Task) extends Command

  // Private command protocol
  private final case class ChildCrashed(
      ref: ActorRef[Nothing],
      reason: Throwable
  ) extends Command
  private final case class ChildTerminated(ref: ActorRef[Nothing])
      extends Command

  // Response protocol
  sealed trait Response
  sealed trait FailureResponse extends Response

  final case class TaskPass(task: Task) extends Response
  final case class TaskHalt(task: Task) extends Response
  final case class TaskExecutionError(task: Task, reason: Throwable)
      extends FailureResponse

  private type CommandOrResponse = Command | ExecutionWorker.Response

  def apply(
      replyTo: ActorRef[Response]
  ): Behavior[CommandOrResponse] =
    setup(replyTo)
  end apply

  def setup(
      replyTo: ActorRef[Response]
  ): Behavior[CommandOrResponse] =
    Behaviors.setup { context =>
      context.log.info("ExecutionManager started...")
      handleMessages(replyTo)
    }
  end setup

  /** Handles messages received by the actor.
    *
    * @param replyTo
    *   Reference to reply to.
    * @param failureResponse
    *   Map of child references to failure response functions in case of a child
    *   failure.
    */
  def handleMessages(
      replyTo: ActorRef[Response],
      failureResponse: Map[ActorRef[Nothing], Throwable => FailureResponse] =
        Map()
  ): Behavior[CommandOrResponse] =
    Behaviors
      .receive[CommandOrResponse] { (context, message) =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          case ExecuteTask(task) =>
            // delegateTaskExecution(context, task)
            val supervisedWorker =
              Behaviors
                .supervise(ExecutionWorker())
                .onFailure(SupervisorStrategy.stop)
            val worker =
              context.spawnAnonymous(supervisedWorker)
            context.watch(worker)

            worker ! ExecutionWorker.ExecuteTask(task, context.self)

            handleMessages(
              replyTo,
              failureResponse + (worker -> (th => TaskExecutionError(task, th)))
            )

          /* **********************************************************************
           * Private commands
           * ********************************************************************** */

          case ChildCrashed(ref, reason) =>
            failureResponse.get(ref) match
              case Some(errorApplicationFunction) =>
                replyTo ! errorApplicationFunction(reason)
                handleMessages(replyTo, failureResponse - ref)
              case None =>
                context.log.error(
                  s"Reference not found - $ref. Crash reason - $reason"
                )
                Behaviors.same
            end match

          case ChildTerminated(ref) =>
            if failureResponse.contains(ref) then
              handleMessages(replyTo, failureResponse - ref)
            else
              context.log.error(s"Reference $ref not found.")
              Behaviors.same
            end if

          /* **********************************************************************
           * Responses
           * ********************************************************************** */

          case ExecutionWorker.TaskPass(task) =>
            replyTo ! TaskPass(task)
            Behaviors.same

          case ExecutionWorker.TaskHalt(task) =>
            replyTo ! TaskHalt(task)
            Behaviors.same
      }
      .receiveSignal {
        case (context, ChildFailed(ref, reason)) =>
          context.self ! ChildCrashed(ref, reason)
          Behaviors.same

        case (context, Terminated(ref)) =>
          context.self ! ChildTerminated(ref)
          Behaviors.same
      }
  end handleMessages
end ExecutionManager

/** @author
  *   Esteban Gonzalez Ruales
  */

package actors.execution

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import types.Task

import scala.util.Failure
import scala.util.Success
import scala.util.Try

object ExecutionWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ExecuteTask(
      task: Task,
      replyTo: ActorRef[StatusReply[Task]]
  ) extends Command

  def apply(): Behavior[Command] = processing()

  /** This behavior represents the processing state of the actor.
    *
    * @return
    *   A behavior that processes a task and then stops.
    */
  def processing(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case ExecuteTask(task, replyTo) =>
          context.log.info(
            s"ExecuteTask command received. Task --> $task."
          )

          executionResult(task) match
            case Success(executedTask) =>
              context.log.info(
                s"Execution success. Task --> $task."
              )
              context.log.info(
                s"Sending StatusReply.Success to ExecutionManager. Task --> $task."
              )

              replyTo ! StatusReply.Success(executedTask)
            case Failure(exception) =>
              context.log.error(
                s"Execution failed. Task --> $task. Exception thrown: ${exception.getMessage()}."
              )
              context.log.info(
                s"Sending StatusReply.Error to ExecutionManager. Task --> $task."
              )

              replyTo ! StatusReply.Error(exception)
          end match
      end match

      Behaviors.stopped
    }
  end processing

  private def executionResult(task: Task): Try[Task] =
    Try {
      task.processingStages.headOption match
        case Some("PARSING") =>
          val endTime = System.currentTimeMillis() + 7000
          while System.currentTimeMillis() < endTime do ()
          end while
          task
        case Some("EXECUTION") =>
          val endTime = System.currentTimeMillis() + 10000
          while System.currentTimeMillis() < endTime do ()
          end while
          task
        case _ =>
          throw Exception("Task processing stage not found or empty.")
      end match
    }
  end executionResult
end ExecutionWorker

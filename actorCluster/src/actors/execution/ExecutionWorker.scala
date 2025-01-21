/** @author
  *   Esteban Gonzalez Ruales
  */

package actors.execution

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import akka.util.Timeout
import os.Path
import types.Task

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object ExecutionWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ExecuteTask(
      task: Task,
      path: Path,
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
        case ExecuteTask(task, path, replyTo) =>
          context.log.info(
            s"ExecuteTask command received. Task --> $task, Path --> $path."
          )

          executionResult(task, path) match
            case Success(executedTask) =>
              context.log.info(
                s"Execution success. Task --> $task, Path --> $path."
              )
              context.log.info(
                s"Sending StatusReply.Success to ExecutionManager. Task --> $task."
              )

              replyTo ! StatusReply.Success(executedTask)
            case Failure(exception) =>
              context.log.error(
                s"Execution failed. Task --> $task, Path --> $path. Exception thrown: ${exception.getMessage()}."
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

  private def executionResult(task: Task, path: Path): Try[Task] =
    Try {
      task.processingStages.headOption match
        case Some("processing") =>
          val endTime = System.currentTimeMillis() + 5000
          while System.currentTimeMillis() < endTime do ()
          end while
          task
        case Some("execution") =>
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

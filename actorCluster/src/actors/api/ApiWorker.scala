package actors.api

/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import types.Task

import scala.util.Failure
import scala.util.Success
import scala.util.Try

object ApiWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ApiUpdateTask(
      task: Task,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  def apply(): Behavior[Command] = sendUpdate()

  def sendUpdate(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case ApiUpdateTask(task, replyTo) =>
          // Send the task to the orchestrator
          context.log
            .info(
              s"ApiWorker received task with id: ${task.taskId}. Attempting update in API..."
            )

          updateInApi(task) match
            case Success(updatedTask) =>
              context.log
                .info(
                  s"ApiWorker successfully updated task with id: ${updatedTask.taskId}. Notifying ApiManager..."
                )
              replyTo ! StatusReply.Success(Done)
            case Failure(exception) =>
              context.log.error(
                s"ApiWorker failed to update task with id: ${task.taskId}. Notifying ApiManager..."
              )
              replyTo ! StatusReply.Error(exception)
          end match
      end match

      Behaviors.stopped
    }

  private def updateInApi(task: Task): Try[Task] =
    val endTime = System.currentTimeMillis() + 5000
    while System.currentTimeMillis() < endTime do ()
    end while
    Try(task)
  end updateInApi
end ApiWorker

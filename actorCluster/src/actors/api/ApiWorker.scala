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
  final case class ApiTaskLog(
      task: Task,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  def apply(): Behavior[Command] = sendUpdate()

  def sendUpdate(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case ApiTaskLog(task, replyTo) =>
          // Send the task to the orchestrator
          context.log.info(s"ApiTaskLog command received. Task --> $task.")

          updateInApi(task) match
            case Success(updatedTask) =>
              context.log.info(s"Api request success. Task --> $task.")
              context.log.info(
                s"Sending StatusReply.Ack to ApiManager. Task --> $task."
              )
              replyTo ! StatusReply.Ack
            case Failure(exception) =>
              context.log.error(
                s"Api request failed. Task --> $task. Exception thrown: ${exception.getMessage()}."
              )
              context.log.error(
                s"Sending StatusReply.Error to ApiManager. Task --> $task."
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

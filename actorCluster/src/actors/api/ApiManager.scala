package actors.api

/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.Done
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import types.Task

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

private val DefaultApiRetires = 5

object ApiManager:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ApiTaskLog(task: Task, retries: Int = DefaultApiRetires)
      extends Command

  // Internal command protocol
  private case object NoOp extends Command

  // Implicit timeout for ask pattern
  implicit val timeout: Timeout = 10.seconds

  def apply(): Behavior[Command] = processing()

  def processing(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case ApiTaskLog(task, retries) =>
          context.log.info(
            s"ApiTaskLog command received. Task --> $task."
          )

          val apiWorker = context.spawnAnonymous(ApiWorker())

          context.askWithStatus[ApiWorker.ApiTaskLog, Done](
            apiWorker,
            replyTo => ApiWorker.ApiTaskLog(task, replyTo)
          ) {
            case Success(Done) =>
              context.log.info(
                s"Request delivery success response received from worker. Task --> $task."
              )
              NoOp
            case Failure(exception) =>
              val failureMessage =
                s"Request delivery failure response received from worker. Task --> $task. Exception thrown: ${exception
                    .getMessage()}. $retries retries left."

              if retries > 0 then
                context.log.error(s"$failureMessage Retrying...")
                ApiTaskLog(task, retries - 1)
              else
                context.log.error(s"$failureMessage Retries exhausted.")
                NoOp
              end if
          }
          Behaviors.same

        case NoOp =>
          Behaviors.same

      end match

    }

end ApiManager

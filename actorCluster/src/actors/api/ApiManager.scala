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
  final case class ApiTaskUpdate(task: Task, retries: Int = DefaultApiRetires)
      extends Command

  // Internal command protocol
  private final case class Report(message: String) extends Command

  // Implicit timeout for ask pattern
  implicit val timeout: Timeout = 10.seconds

  def apply(): Behavior[Command] = processing()

  def processing(): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case ApiTaskUpdate(task, retries) =>
          context.log.info(
            s"ApiManager received task with id: ${task.taskId}. Attempting update in API..."
          )

          val apiWorker = context.spawnAnonymous(ApiWorker())

          context.askWithStatus[ApiWorker.ApiUpdateTask, Done](
            apiWorker,
            replyTo => ApiWorker.ApiUpdateTask(task, replyTo)
          ) {
            case Success(Done) =>
              context.log.info(
                s"ApiManager successfully updated task with id: ${task.taskId}."
              )
              Report(s"Task ${task.taskId} updated successfully.")
            case Failure(exception) =>
              if retries > 0 then
                context.log.error(
                  s"ApiManager failed to update task with id: ${task.taskId}. Retrying..."
                )
                ApiTaskUpdate(task, retries - 1)
              else
                context.log.error(
                  s"ApiManager failed to update task with id: ${task.taskId}. Retries exhausted."
                )
                Report(s"Task ${task.taskId} failed to update.")
          }

        case Report(message) =>
          context.log.info(message)
      end match

      Behaviors.same
    }

end ApiManager

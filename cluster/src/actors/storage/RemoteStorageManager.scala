package actors.storage

import scala.concurrent.duration.*

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.ChildFailed
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.Terminated
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import types.RemoteStorageConnectionParams
import types.Task

/** A persistent actor responsible for managing actors with remote storage
  * related tasks. It acts as the intermediary between the remote storage and
  * the system that processes the tasks.
  */
object RemoteStorageManager:
  given Timeout = 20.seconds

  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class DownloadTaskFiles(
      task: Task
  ) extends Command
  final case class UploadTaskFiles(
      task: Task
  ) extends Command

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

  final case class TaskDownloaded(task: Task) extends Response
  final case class TaskUploaded(task: Task) extends Response
  final case class TaskDownloadFailed(task: Task, reason: Throwable)
      extends FailureResponse
  final case class TaskUploadFailed(task: Task, reason: Throwable)
      extends FailureResponse

  private enum FailType:
    case DownloadFailure
    case UploadFailure
  end FailType

  private type CommandOrResponse = Command | RemoteStorageWorker.Response

  def apply(
      connParams: RemoteStorageConnectionParams,
      replyTo: ActorRef[Response]
  ): Behavior[CommandOrResponse] =
    setup(
      connParams,
      replyTo
    )

    /** Sets up the actor.
      *
      * @param connParams
      *   parameters for connecting to the remote storage
      * @param replyTo
      *   reference to the Orchestrator actor
      */
  private def setup(
      connParams: RemoteStorageConnectionParams,
      replyTo: ActorRef[Response]
  ): Behavior[CommandOrResponse] = Behaviors.setup { context =>
    context.log.info("RemoteFileManager started...")

    handleMessages(
      connParams,
      replyTo
    )
  }

  /** Handles messages received by the actor.
    *
    * @param connParams
    *   parameters for connecting to the remote storage
    * @param replyTo
    *   reference to the Orchestrator actor
    *
    * @return
    *   A Behavior that handles messages received by the actor.
    */
  private def handleMessages(
      connParams: RemoteStorageConnectionParams,
      replyTo: ActorRef[Response],
      failureResponse: Map[ActorRef[?], (Task, FailType)] = Map()
  ): Behavior[CommandOrResponse] =
    Behaviors
      .receive[CommandOrResponse] { (context, message) =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          case DownloadTaskFiles(task) =>
            val supervisedWorker = Behaviors
              .supervise(RemoteStorageWorker(connParams))
              .onFailure(
                SupervisorStrategy.stop
              )
            val worker = context.spawnAnonymous(supervisedWorker)
            context.watch(worker)

            worker ! RemoteStorageWorker.DownloadFiles(task, context.self)

            handleMessages(
              connParams,
              replyTo,
              failureResponse + (worker -> (task, FailType.DownloadFailure))
            )

          case UploadTaskFiles(task) =>
            val supervisedWorker = Behaviors
              .supervise(RemoteStorageWorker(connParams))
              .onFailure(
                SupervisorStrategy.stop
              )
            val worker = context.spawnAnonymous(supervisedWorker)
            context.watch(worker)

            worker ! RemoteStorageWorker.UploadFiles(task, context.self)

            handleMessages(
              connParams,
              replyTo,
              failureResponse + (worker -> (task, FailType.UploadFailure))
            )

          /* **********************************************************************
           * Private commands
           * ********************************************************************** */

          case ChildCrashed(ref, reason) =>
            failureResponse.get(ref) match
              case Some((task, FailType.DownloadFailure)) =>
                replyTo ! TaskDownloadFailed(task, reason)
                handleMessages(connParams, replyTo, failureResponse - ref)

              case Some((task, FailType.UploadFailure)) =>
                replyTo ! TaskUploadFailed(task, reason)
                handleMessages(connParams, replyTo, failureResponse - ref)

              case None =>
                context.log.error(s"Reference $ref not found.")
                Behaviors.same
            end match

          case ChildTerminated(ref) =>
            if failureResponse.contains(ref) then
              handleMessages(connParams, replyTo, failureResponse - ref)
            else
              context.log.error(s"Reference $ref not found.")
              Behaviors.same
            end if

          /* **********************************************************************
           * Responses
           * ********************************************************************** */

          case RemoteStorageWorker.TaskDownloaded(task) =>
            replyTo ! TaskDownloaded(task)
            Behaviors.same

          case RemoteStorageWorker.TaskUploaded(task) =>
            replyTo ! TaskUploaded(task)
            Behaviors.same

        end match
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
end RemoteStorageManager

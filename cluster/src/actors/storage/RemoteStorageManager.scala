package actors.storage

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.ChildFailed
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.Terminated
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import types.RemoteStorageConnectionParams
import types.Task

/** A persistent actor responsible for managing actors with remote storage
  * related tasks. It acts as the intermediary between the remote storage and
  * the system that processes the tasks.
  */
object RemoteStorageManager:

  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class DownloadTaskFiles(
      task: Task
  ) extends Command
  final case class UploadTaskFiles(
      task: Task
  ) extends Command
  final case class DeleteFiles(
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
    context.log.info("RemoteStorageManager started...")

    handleMessages(
      connParams,
      replyTo
    )
  }

  /** Handles messages received by the actor.
    *
    * @param connParams
    *   Parameters for connecting to the remote storage
    * @param replyTo
    *   Reference to reply to.
    * @param failureResponse
    *   Map of child references to failure response functions in case of a child
    *   failure.
    *
    * @return
    *   A Behavior that handles messages received by the actor.
    */
  private def handleMessages(
      connParams: RemoteStorageConnectionParams,
      replyTo: ActorRef[Response],
      failureResponse: Map[ActorRef[?], Throwable => FailureResponse] = Map()
  ): Behavior[CommandOrResponse] =
    Behaviors
      .receive[CommandOrResponse] { (context, message) =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          case DownloadTaskFiles(task) =>
            val supervisedWorker = Behaviors
              .supervise(RemoteStorageWorker(connParams, context.self))
              .onFailure(SupervisorStrategy.stop)
            val worker = context.spawnAnonymous(supervisedWorker)
            context.watch(worker)

            worker ! RemoteStorageWorker.DownloadFiles(task)

            handleMessages(
              connParams,
              replyTo,
              failureResponse + (worker -> (th => TaskDownloadFailed(task, th)))
            )

          case UploadTaskFiles(task) =>
            val supervisedWorker = Behaviors
              .supervise(RemoteStorageWorker(connParams, context.self))
              .onFailure(SupervisorStrategy.stop)
            val worker = context.spawnAnonymous(supervisedWorker)
            context.watch(worker)

            worker ! RemoteStorageWorker.UploadFiles(task)

            handleMessages(
              connParams,
              replyTo,
              failureResponse + (worker -> (th => TaskUploadFailed(task, th)))
            )

          case DeleteFiles(task) =>
            val supervisedWorker = Behaviors
              .supervise(RemoteStorageWorker(connParams, context.self))
              .onFailure(SupervisorStrategy.stop)
            val worker = context.spawnAnonymous(supervisedWorker)

            worker ! RemoteStorageWorker.DeleteFiles(task)

            Behaviors.same

          /* **********************************************************************
           * Private commands
           * ********************************************************************** */

          case ChildCrashed(ref, reason) =>
            failureResponse.get(ref) match
              case Some(errorApplicationFunction) =>
                replyTo ! errorApplicationFunction(reason)
                handleMessages(connParams, replyTo, failureResponse - ref)

              case None =>
                context.log.error(
                  s"Reference not found - $ref. Crash reason - $reason"
                )
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

          case RemoteStorageWorker.TaskDeleted(task) =>
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

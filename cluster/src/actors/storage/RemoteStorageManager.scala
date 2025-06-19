package actors.storage

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

import actors.Orchestrator
import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import types.RemoteStorageConnectionParams
import types.Task
import utilities.MiscUtils

private val DefaultRemoteOpsRetries = 5

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
      task: Task,
      retries: Int = DefaultRemoteOpsRetries
  ) extends Command
  final case class UploadTaskFiles(
      task: Task,
      retries: Int = DefaultRemoteOpsRetries
  ) extends Command

  // Internal command protocol
  private final case class ReportTaskDownloaded(task: Task) extends Command
  private final case class ReportTaskUploaded(task: Task) extends Command
  private final case class ReportTaskDownloadFailed(task: Task) extends Command
  private final case class ReportTaskUploadFailed(task: Task) extends Command
  private final case class NotifyFatalFailure(th: Throwable) extends Command

  // Response protocol
  sealed trait Response
  final case class TaskDownloaded(task: Task) extends Response
  final case class TaskUploaded(task: Task) extends Response
  final case class TaskDownloadFailed(task: Task) extends Response
  final case class TaskUploadFailed(task: Task) extends Response

  def apply(
      connParams: RemoteStorageConnectionParams,
      replyTo: ActorRef[Response | Orchestrator.Fail]
  ): Behavior[Command] =
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
      replyTo: ActorRef[Response | Orchestrator.Fail]
  ): Behavior[Command] = Behaviors.setup { context =>
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
      replyTo: ActorRef[Response | Orchestrator.Fail]
  ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case DownloadTaskFiles(task, retries) =>
          val _ = delegateDownloadTaskFiles(
            context,
            connParams,
            task,
            retries
          )
          Behaviors.same

        case UploadTaskFiles(task, retries) =>
          val _ = delegateUploadTaskFiles(
            context,
            connParams,
            task,
            retries
          )
          Behaviors.same

        /* **********************************************************************
         * Internal commands
         * ********************************************************************** */

        case ReportTaskDownloaded(task) =>
          replyTo ! TaskDownloaded(task)
          Behaviors.same

        case ReportTaskUploaded(task) =>
          replyTo ! TaskUploaded(task)
          Behaviors.same

        case ReportTaskDownloadFailed(task) =>
          replyTo ! TaskDownloadFailed(task)
          Behaviors.same

        case ReportTaskUploadFailed(task) =>
          replyTo ! TaskUploadFailed(task)
          Behaviors.same

        case NotifyFatalFailure(message) =>
          replyTo ! Orchestrator.Fail(message)
          Behaviors.same

      end match

    }
  end handleMessages

  /** Delegate the download of files for a task to a remote storage worker and
    * handle any errors.
    *
    * @param context
    *   The actor context.
    * @param connParams
    *   The connection parameters for the remote storage worker.
    * @param task
    *   The task to download files for.
    * @param retries
    *   The number of retries left.
    *
    * @return
    *   Unit
    */
  private def delegateDownloadTaskFiles(
      context: ActorContext[Command],
      connParams: RemoteStorageConnectionParams,
      task: Task,
      retries: Int
  ): Unit =
    val downloader = context.spawnAnonymous(RemoteStorageWorker(connParams))

    context.askWithStatus[RemoteStorageWorker.DownloadFiles, Done](
      downloader,
      replyTo =>
        RemoteStorageWorker.DownloadFiles(
          task,
          replyTo
        )
    ) {
      case Success(Done) =>
        ReportTaskDownloaded(task)
      case Failure(th) =>
        val failureMessage =
          s"Download failure for Task --> $task: ${th.getMessage}"

        MiscUtils.defineRetryCommand(
          context,
          retries,
          failureMessage,
          DownloadTaskFiles(task, retries - 1),
          NotifyFatalFailure(th)
        )
    }
  end delegateDownloadTaskFiles

  /** Delegate the download of files for a task to a remote storage worker and
    * handle any errors.
    *
    * @param context
    *   The actor context.
    * @param connParams
    *   The connection parameters for the remote storage worker.
    * @param task
    *   The task to download files for.
    * @param retries
    *   The number of retries remaining.
    *
    * @return
    *   Unit
    */
  private def delegateUploadTaskFiles(
      context: ActorContext[Command],
      connParams: RemoteStorageConnectionParams,
      task: Task,
      retries: Int
  ): Unit =
    val uploader = context.spawnAnonymous(RemoteStorageWorker(connParams))

    context.askWithStatus[RemoteStorageWorker.UploadFiles, Done](
      uploader,
      replyTo =>
        RemoteStorageWorker.UploadFiles(
          task,
          replyTo
        )
    ) {
      case Success(Done) =>
        ReportTaskUploaded(task)
      case Failure(th) =>
        val failureMessage =
          s"Upload failure for Task --> $task: ${th.getMessage}"

        MiscUtils.defineRetryCommand(
          context,
          retries,
          failureMessage,
          UploadTaskFiles(task, retries - 1),
          NotifyFatalFailure(th)
        )
    }
  end delegateUploadTaskFiles
end RemoteStorageManager

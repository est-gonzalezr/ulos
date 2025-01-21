package actors.files

import akka.Done
import akka.actor.ProviderSelection.Remote
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import os.Path
import types.OpaqueTypes.RemoteStorageHost
import types.OpaqueTypes.RemoteStoragePassword
import types.OpaqueTypes.RemoteStoragePort
import types.OpaqueTypes.RemoteStorageUser
import types.Task

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import os.RelPath

private val DefaultRemoteOpsRetries = 5

object RemoteFileManager:
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
  private final case class ReportTaskDownloaded(task: Task, path: Path)
      extends Command
  private final case class ReportTaskUploaded(task: Task) extends Command
  private final case class ReportTaskDownloadFailed(task: Task) extends Command
  private final case class ReportTaskUploadFailed(task: Task) extends Command

  // Response protocol
  sealed trait Response
  final case class TaskDownloaded(task: Task, path: Path) extends Response
  final case class TaskUploaded(task: Task) extends Response
  final case class TaskDownloadFailed(task: Task) extends Response
  final case class TaskUploadFailed(task: Task) extends Response

  implicit val timeout: Timeout = 10.seconds

  def apply(
      remoteStorageHost: RemoteStorageHost,
      remoteStoragePort: RemoteStoragePort,
      remoteStorageUser: RemoteStorageUser,
      remoteStoragePass: RemoteStoragePassword,
      maxWorkers: Int,
      replyTo: ActorRef[Response]
  ): Behavior[Command] =
    setup(
      remoteStorageHost,
      remoteStoragePort,
      remoteStorageUser,
      remoteStoragePass,
      0,
      maxWorkers,
      replyTo
    )

  def setup(
      remoteStorageHost: RemoteStorageHost,
      remoteStoragePort: RemoteStoragePort,
      remoteStorageUser: RemoteStorageUser,
      remoteStoragePass: RemoteStoragePassword,
      activeWorkers: Int,
      maxWorkers: Int,
      replyTo: ActorRef[Response]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("RemoteFileManager started...")

      Behaviors.receiveMessage { message =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          /* DownloadTaskFiles
           *
           * Download a file from a remote location.
           */
          case DownloadTaskFiles(task, retries) =>
            context.log.info(
              s"DownloadTaskFiles command received. Task --> $task."
            )

            context.log.info(s"Spawning downloader...")
            val downloader = context.spawnAnonymous(RemoteFileWorker())

            context.log.info(s"Downloader spawned.")
            context.log.info(s"Sending task to downloader. Task --> $task.")

            context.askWithStatus[RemoteFileWorker.DownloadFile, Path](
              downloader,
              replyTo =>
                RemoteFileWorker.DownloadFile(
                  remoteStorageHost,
                  remoteStoragePort,
                  remoteStorageUser,
                  remoteStoragePass,
                  RelPath(task.taskPath),
                  replyTo
                )
            ) {
              case Success(path) =>
                context.log.info(
                  s"Download success response received from downloader. Task awaiting rerouting to orchestrator. Task --> $task, Path --> $path."
                )
                ReportTaskDownloaded(task, path)
              case Failure(exception) =>
                val failureMessage =
                  s"Download failure response received from downloader. Task --> $task. Exception thrown: ${exception.getMessage}. $retries retries left."

                if retries > 0 then
                  context.log.warn(s"$failureMessage Retrying...")
                  DownloadTaskFiles(task, retries - 1)
                else
                  context.log.error(s"$failureMessage Retries exhausted.")
                  ReportTaskDownloadFailed(
                    task.copy(errorMessage = Some(exception.getMessage()))
                  )
                end if
            }
            Behaviors.same

          /* UploadTaskFiles
           *
           * Upload a file to a remote location.
           */
          case UploadTaskFiles(task, retries) =>
            context.log.info(
              s"UploadTaskFiles command received. Task --> $task."
            )

            context.log.info(s"Spawning uploader...")
            val uploader = context.spawnAnonymous(RemoteFileWorker())

            context.log.info(s"Uploader spawned.")
            context.log.info(s"Sending task to uploader. Task --> $task.")

            context.askWithStatus[RemoteFileWorker.UploadFile, Done](
              uploader,
              replyTo =>
                RemoteFileWorker.UploadFile(
                  remoteStorageHost,
                  remoteStoragePort,
                  remoteStorageUser,
                  remoteStoragePass,
                  RelPath(task.taskPath),
                  replyTo
                )
            ) {
              case Success(Done) =>
                context.log.info(
                  s"Upload success response received from uploader. Task awaiting rerouting to orchestrator. Task --> $task."
                )
                ReportTaskUploaded(task)
              case Failure(exception) =>
                val failureMessage =
                  s"Upload failure response received from uploader. Task --> $task. Exception thrown: ${exception.getMessage}. $retries retries left."

                if retries > 0 then
                  context.log.warn(s"$failureMessage Retrying...")
                  UploadTaskFiles(task, retries - 1)
                else
                  context.log.error(s"$failureMessage Retries exhausted.")
                  ReportTaskUploadFailed(
                    task.copy(errorMessage = Some(exception.getMessage))
                  )
                end if
            }

            Behaviors.same

          /* **********************************************************************
           * Internal commands
           * ********************************************************************** */

          case ReportTaskDownloaded(task, path) =>
            context.log.info(
              s"ReportTaskDownloaded command received. Task --> $task, Path --> $path."
            )
            context.log.info(
              s"Sending TaskDownloaded to orchestrator. Task --> $task, Path --> $path."
            )
            replyTo ! TaskDownloaded(task, path)
            Behaviors.same

          case ReportTaskUploaded(task) =>
            context.log.info(
              s"ReportTaskUploaded command received. Task --> $task."
            )
            context.log.info(
              s"Sending TaskUploaded to orchestrator. Task --> $task."
            )
            replyTo ! TaskUploaded(task)
            Behaviors.same

          case ReportTaskDownloadFailed(task) =>
            context.log.info(
              s"ReportTaskDownloadFailed command received. Task --> $task."
            )
            context.log.info(
              s"Sending TaskDownloadFailed to orchestrator. Task --> $task."
            )
            replyTo ! TaskDownloadFailed(task)
            Behaviors.same

          case ReportTaskUploadFailed(task) =>
            context.log.info(
              s"ReportTaskUploadFailed command received. Task --> $task."
            )
            context.log.info(
              s"Sending TaskUploadFailed to orchestrator. Task --> $task."
            )
            replyTo ! TaskUploadFailed(task)
            Behaviors.same
      }
    }
  end setup
end RemoteFileManager

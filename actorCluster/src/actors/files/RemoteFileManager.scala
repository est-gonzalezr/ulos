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
  final case class SetMaxRemoteFileWorkers(maxWorkers: Int) extends Command
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

      def delegateProcessing(
          activeWorkers: Int,
          maxWorkers: Int,
          replyTo: ActorRef[Response]
      ): Behavior[Command] =
        Behaviors.receiveMessage { message =>
          message match

            /* **********************************************************************
             * Public commands
             * ********************************************************************** */

            /* SetMaxRemoteFileWorkers
             *
             * Set the maximum number of remote file workers that can be active at
             * any given time.
             */
            case SetMaxRemoteFileWorkers(maxWorkers) =>
              context.log.info(
                s"RemoteFileManagerm setting max remote file workers to $maxWorkers"
              )
              delegateProcessing(activeWorkers, maxWorkers, replyTo)

            /* DownloadTaskFiles
             *
             * Download a file from a remote location.
             */
            case DownloadTaskFiles(task, retries) =>
              context.log.info(
                s"RemoteFileManager received download task with id: ${task.taskId}, type: ${task.taskType}, path: ${task.taskPath}. Attempting to spwan downloader worker..."
              )
              val downloader = context.spawnAnonymous(RemoteFileWorker())

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
                    s"RemoteFileManager received downloaded file for task with id: ${task.taskId}, type: ${task.taskType}, path: ${task.taskPath}. Task awaiting rerouting to orchestrator..."
                  )
                  ReportTaskDownloaded(task, path)
                case Failure(throwable) =>
                  if retries > 0 then
                    context.log.warn(
                      s"RemoteFileManager failed to download file for task with id: ${task.taskId}, type: ${task.taskType}, path: ${task.taskPath}. Retrying..."
                    )
                    DownloadTaskFiles(task, retries - 1)
                  else
                    context.log.error(
                      s"RemoteFileManager failed to download file for task with id: ${task.taskId}, type: ${task.taskType}, path: ${task.taskPath} with error: ${throwable.getMessage}. Retries exhausted."
                    )
                    ReportTaskDownloadFailed(
                      task.copy(errorMessage = Some(throwable.getMessage))
                    )
              }

              delegateProcessing(activeWorkers + 1, maxWorkers, replyTo)

            /* UploadTaskFiles
             *
             * Upload a file to a remote location.
             */
            case UploadTaskFiles(task, retries) =>
              context.log.info(
                s"RemoteFileManager received upload task with id: ${task.taskId}, type: ${task.taskType}, path: ${task.taskPath}. Attempting to spwan uploader worker..."
              )
              val uploader = context.spawnAnonymous(RemoteFileWorker())

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
                case Success(_) =>
                  context.log.info(
                    s"RemoteFileManager received uploaded file for task with id: ${task.taskId}, type: ${task.taskType}, path: ${task.taskPath}. Task awaiting rerouting to orchestrator..."
                  )
                  ReportTaskUploaded(task)
                case Failure(throwable) =>
                  if retries > 0 then
                    context.log.warn(
                      s"RemoteFileManager failed to upload file for task with id: ${task.taskId}, type: ${task.taskType}, path: ${task.taskPath}. Retrying..."
                    )
                    UploadTaskFiles(task, retries - 1)
                  else
                    context.log.error(
                      s"RemoteFileManager failed to upload file for task with id: ${task.taskId}, type: ${task.taskType}, path: ${task.taskPath} with error: ${throwable.getMessage}. Retries exhausted."
                    )
                    ReportTaskUploadFailed(
                      task.copy(errorMessage = Some(throwable.getMessage))
                    )
              }

              delegateProcessing(activeWorkers + 1, maxWorkers, replyTo)

            /* **********************************************************************
             * Internal commands
             * ********************************************************************** */

            case ReportTaskDownloaded(task, path) =>
              context.log.info(s"Downloaded file: ${task.taskType}")
              replyTo ! TaskDownloaded(task, path)
              delegateProcessing(activeWorkers - 1, maxWorkers, replyTo)

            case ReportTaskUploaded(task) =>
              context.log.info(s"Uploaded file: ${task.taskType}")
              replyTo ! TaskUploaded(task)
              delegateProcessing(activeWorkers - 1, maxWorkers, replyTo)

            case ReportTaskDownloadFailed(task) =>
              context.log.warn(s"Failed to download file: ${task.taskType}")
              replyTo ! TaskDownloadFailed(task)
              delegateProcessing(activeWorkers - 1, maxWorkers, replyTo)

            case ReportTaskUploadFailed(task) =>
              context.log.warn(s"Failed to upload file: ${task.taskType}")
              replyTo ! TaskUploadFailed(task)
              delegateProcessing(activeWorkers - 1, maxWorkers, replyTo)
        }

      delegateProcessing(activeWorkers, maxWorkers, replyTo)
    }
  end setup
end RemoteFileManager

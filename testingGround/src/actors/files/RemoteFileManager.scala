package actors.files

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import types.Task
import types.OpaqueTypes.Uri
import types.OpaqueTypes.LocalPath

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

object RemoteFileManager:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class SetMaxRemoteFileWorkers(maxWorkers: Int) extends Command
  final case class DownloadTaskFiles(task: Task) extends Command
  final case class UploadTaskFiles(task: Task) extends Command

  // Internal command protocol
  private final case class ReportTaskDownloaded(task: Task) extends Command
  private final case class ReportTaskUploaded(task: Task) extends Command
  private final case class ReportTaskDownloadFailed(task: Task) extends Command
  private final case class ReportTaskUploadFailed(task: Task) extends Command

  // Response protocol
  sealed trait Response
  final case class SuccessfulDownload(task: Task) extends Response
  final case class SuccessfulUpload(task: Task) extends Response
  final case class FailedDownload(task: Task) extends Response
  final case class FailedUpload(task: Task) extends Response

  implicit val timeout: Timeout = 10.seconds

  def apply(maxWorkers: Int, ref: ActorRef[Response]): Behavior[Command] =
    delegateProcessing(0, maxWorkers, ref)

  def delegateProcessing(
      activeWorkers: Int,
      maxWorkers: Int,
      ref: ActorRef[Response]
  ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case SetMaxRemoteFileWorkers(maxWorkers) =>
          context.log.info(
            s"Setting max remote file workers to $maxWorkers"
          )
          delegateProcessing(activeWorkers, maxWorkers, ref)

        case DownloadTaskFiles(task) =>
          val remoteFilesWorker = context.spawnAnonymous(RemoteFileWorker())

          context.askWithStatus[RemoteFileWorker.DownloadFile, LocalPath](
            remoteFilesWorker,
            ref => RemoteFileWorker.DownloadFile(Uri(task.taskUri), ref)
          ) {
            case Success(localPath) =>
              ReportTaskDownloaded(task)
            case Failure(throwable) =>
              ReportTaskDownloadFailed(
                task.copy(errorMessage = Some(throwable.getMessage))
              )
          }

          delegateProcessing(activeWorkers + 1, maxWorkers, ref)

        case UploadTaskFiles(task) =>
          val remoteFilesWorker = context.spawnAnonymous(RemoteFileWorker())

          context.askWithStatus[RemoteFileWorker.UploadFile, Done](
            remoteFilesWorker,
            ref => RemoteFileWorker.UploadFile(Uri(task.taskUri), ref)
          ) {
            case Success(_) =>
              ReportTaskUploaded(task)
            case Failure(throwable) =>
              ReportTaskUploadFailed(
                task.copy(errorMessage = Some(throwable.getMessage))
              )
          }

          delegateProcessing(activeWorkers + 1, maxWorkers, ref)

        /* **********************************************************************
         * Internal commands
         * ********************************************************************** */

        case ReportTaskDownloaded(task) =>
          context.log.info(s"Downloaded file: ${task.taskType}")
          ref ! SuccessfulDownload(task)
          delegateProcessing(activeWorkers - 1, maxWorkers, ref)

        case ReportTaskUploaded(task) =>
          context.log.info(s"Uploaded file: ${task.taskType}")
          ref ! SuccessfulUpload(task)
          delegateProcessing(activeWorkers - 1, maxWorkers, ref)

        case ReportTaskDownloadFailed(task) =>
          context.log.warn(s"Failed to download file: ${task.taskType}")
          ref ! FailedDownload(task)
          delegateProcessing(activeWorkers - 1, maxWorkers, ref)

        case ReportTaskUploadFailed(task) =>
          context.log.warn(s"Failed to upload file: ${task.taskType}")
          ref ! FailedUpload(task)
          delegateProcessing(activeWorkers - 1, maxWorkers, ref)
    }
  end delegateProcessing
end RemoteFileManager

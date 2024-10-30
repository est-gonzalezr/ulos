package actors.files

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import types.Task

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

object RemoteFilesManager:
  // Command protocol
  sealed trait Command

  // Public command protocol
  case object IncreaseProcessors extends Command
  case object DecreaseProcessors extends Command
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

        case IncreaseProcessors =>
          context.log.info(
            s"Increasing max processors from $maxWorkers to ${maxWorkers + 1}"
          )
          delegateProcessing(activeWorkers, maxWorkers + 1, ref)

        case DecreaseProcessors =>
          if maxWorkers > 1 then
            context.log.info(
              s"Decreasing max processors from $maxWorkers to ${maxWorkers - 1}"
            )
            delegateProcessing(activeWorkers, maxWorkers - 1, ref)
          else
            context.log.warn("Cannot decrease processors below 1")
            Behaviors.same

        case DownloadTaskFiles(task) =>
          val remoteFilesWorker = context.spawnAnonymous(RemoteFilesWorker())

          context.askWithStatus[RemoteFilesWorker.DownloadFile, Done](
            remoteFilesWorker,
            ref => RemoteFilesWorker.DownloadFile(task.storageTaskPath, ref)
          ) {
            case Success(_) =>
              ReportTaskDownloaded(task)
            case Failure(throwable) =>
              ReportTaskDownloadFailed(
                task.copy(errorMessage = Some(throwable.getMessage))
              )
          }

          delegateProcessing(activeWorkers + 1, maxWorkers, ref)

        case UploadTaskFiles(task) =>
          val remoteFilesWorker = context.spawnAnonymous(RemoteFilesWorker())

          context.askWithStatus[RemoteFilesWorker.UploadFile, Done](
            remoteFilesWorker,
            ref => RemoteFilesWorker.UploadFile(task.storageTaskPath, ref)
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

//   def processing(
//       activeWorkers: Int,
//       maxWorkers: Int,
//       ref: ActorRef[Response]
//   ): Behavior[Command] =
//     Behaviors.receive { (context, message) =>
//       message match
//         case IncreaseProcessors =>
//           context.log.info(
//             s"Increasing max processors from $maxWorkers to ${maxWorkers + 1}"
//           )
//           processing(activeWorkers, maxWorkers + 1, ref)

//         case DecreaseProcessors =>
//           if maxWorkers > 1 then
//             context.log.info(
//               s"Decreasing max processors from $maxWorkers to ${maxWorkers - 1}"
//             )
//             processing(activeWorkers, maxWorkers - 1, ref)
//           else
//             context.log.warn("Cannot decrease processors below 1")
//             Behaviors.same

//         case UploadTask(str) =>
//           if activeWorkers < maxWorkers then
//             context.log.info(s"Uploading file: $str")

//             val ftpWorker =
//               context.spawn(FtpWorker(), s"ftpWorker-$activeWorkers")

//             context.ask(
//               ftpWorker,
//               ref => FtpWorker.UploadTask(str, ref)
//             ) {
//               case Success(_) =>
//                 ReportFtpJob("Job successful")
//               case Failure(_) =>
//                 ReportFtpJob("Job failed")
//             }

//             processing(activeWorkers + 1, maxWorkers, ref)
//           else
//             context.log.warn("Cannot upload file, all workers are busy")
//             Behaviors.same

//         case DownloadTask(str) =>
//           if activeWorkers < maxWorkers then
//             context.log.info(s"Downloading file: $str")
//             ref ! SuccessfulUpload
//             processing(activeWorkers + 1, maxWorkers, ref)
//           else
//             context.log.warn("Cannot download file, all workers are busy")
//             Behaviors.same

//         case ReportFtpJob(str) =>
//           context.log.info(str)
//           processing(activeWorkers - 1, maxWorkers, ref)
//     }
//   end processing
end RemoteFilesManager

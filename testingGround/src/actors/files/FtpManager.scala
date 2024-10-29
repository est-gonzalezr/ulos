import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

object FtpManager:
  sealed trait Command
  case object IncreaseProcessors extends Command
  case object DecreaseProcessors extends Command
  final case class UploadTask(task: Task) extends Command
  final case class DownloadTask(task: Task) extends Command
  final case class ReportFtpJob(task: Task) extends Command

  sealed trait Response
  case object SuccessfulUpload extends Response
  case object UnsuccessfulUpload extends Response

  implicit val timeout: Timeout = 10.seconds

  def apply(maxWorkers: Int, ref: ActorRef[Response]): Behavior[Command] =
    processing(0, maxWorkers, ref)

  def processing(
      activeWorkers: Int,
      maxWorkers: Int,
      ref: ActorRef[Response]
  ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case IncreaseProcessors =>
          context.log.info(
            s"Increasing max processors from $maxWorkers to ${maxWorkers + 1}"
          )
          processing(activeWorkers, maxWorkers + 1, ref)

        case DecreaseProcessors =>
          if maxWorkers > 1 then
            context.log.info(
              s"Decreasing max processors from $maxWorkers to ${maxWorkers - 1}"
            )
            processing(activeWorkers, maxWorkers - 1, ref)
          else
            context.log.warn("Cannot decrease processors below 1")
            Behaviors.same

        case UploadTask(str) =>
          if activeWorkers < maxWorkers then
            context.log.info(s"Uploading file: $str")

            val ftpWorker =
              context.spawn(FtpWorker(), s"ftpWorker-$activeWorkers")

            context.ask(
              ftpWorker,
              ref => FtpWorker.UploadTask(str, ref)
            ) {
              case Success(_) =>
                ReportFtpJob("Job successful")
              case Failure(_) =>
                ReportFtpJob("Job failed")
            }

            processing(activeWorkers + 1, maxWorkers, ref)
          else
            context.log.warn("Cannot upload file, all workers are busy")
            Behaviors.same

        case DownloadTask(str) =>
          if activeWorkers < maxWorkers then
            context.log.info(s"Downloading file: $str")
            ref ! SuccessfulUpload
            processing(activeWorkers + 1, maxWorkers, ref)
          else
            context.log.warn("Cannot download file, all workers are busy")
            Behaviors.same

        case ReportFtpJob(str) =>
          context.log.info(str)
          processing(activeWorkers - 1, maxWorkers, ref)
    }
  end processing
end FtpManager

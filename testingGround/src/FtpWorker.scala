import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object FtpWorker:
  sealed trait Command
  final case class UploadFile(str: String, replyTo: ActorRef[Response])
      extends Command
  final case class DownloadFile(str: String, replyTo: ActorRef[Response])
      extends Command

  sealed trait Response
  final case class FileDownloader(str: String) extends Response
  case object FileUploaded extends Response

  def apply(): Behavior[Command] = processing

  def processing: Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case UploadFile(str, replyTo) =>
          context.log.info(s"Uploading file: $str")
          replyTo ! FileUploaded
          Behaviors.stopped

        case DownloadFile(str, replyTo) =>
          context.log.info(s"Downloading file: $str")
          replyTo ! FileDownloader(str)
          Behaviors.stopped
    }
  end processing
end FtpWorker

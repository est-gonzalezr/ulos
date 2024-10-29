import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object FtpWorker:
  sealed trait Command
  final case class UploadFile(task: Task, replyTo: ActorRef[Response])
      extends Command
  final case class DownloadFile(task: Task, replyTo: ActorRef[Response])
      extends Command

  sealed trait Response
  final case class FileDownloader(task: Task) extends Response
  case object FileUploaded extends Response

  def apply(): Behavior[Command] = processing

  def processing: Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case UploadFile(task, replyTo) =>
          context.log.info(s"Uploading file: ${task.taskType}")
          replyTo ! FileUploaded
          Behaviors.stopped

        case DownloadFile(task, replyTo) =>
          context.log.info(s"Downloading file: ${task.taskType}")
          replyTo ! FileDownloader(task)
          Behaviors.stopped
    }
  end processing
end FtpWorker

package actors.files

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import types.Task

object RemoteFilesWorker:
  sealed trait Command
  final case class DownloadFile(
      path: String,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class UploadFile(
      path: String,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  sealed trait Response
  final case class FileDownloaded(path: String) extends Response
  case object FileUploaded extends Response

  def apply(): Behavior[Command] = processing

  def processing: Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case UploadFile(path, replyTo) =>
          context.log.info(s"Uploading file:")
          replyTo ! StatusReply.Ack

        case DownloadFile(path, replyTo) =>
          context.log.info(s"Downloading file:")
          replyTo ! StatusReply.Ack
      end match
      Behaviors.stopped
    }
  end processing
end RemoteFilesWorker

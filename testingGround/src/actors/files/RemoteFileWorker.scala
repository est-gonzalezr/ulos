package actors.files

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import types.Task
import types.OpaqueTypes.Uri
import types.OpaqueTypes.LocalPath

object RemoteFileWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class DownloadFile(
      uri: Uri,
      replyTo: ActorRef[StatusReply[LocalPath]]
  ) extends Command
  final case class UploadFile(
      uri: Uri,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  def apply(): Behavior[Command] = processing

  def processing: Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case DownloadFile(uri, replyTo) =>
          context.log.info(s"Downloading file:")
          replyTo ! StatusReply.success(LocalPath(uri.value))

        case UploadFile(uri, replyTo) =>
          context.log.info(s"Uploading file:")
          replyTo ! StatusReply.Ack

      end match
      Behaviors.stopped
    }
  end processing
end RemoteFileWorker

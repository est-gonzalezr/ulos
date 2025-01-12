package actors.files

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import os.Path
import types.OpaqueTypes.Uri
import types.Task
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient

object RemoteFileWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class DownloadFile(
      uri: Uri,
      replyTo: ActorRef[StatusReply[Path]]
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
          context.log.info(s"Downloading file: $uri")
          downloadFile(uri) match
            case Success(bytes) =>
              val path = os.pwd / uri.value
              saveFile(path, bytes)
              replyTo ! StatusReply.success(path)
            case Failure(exception) =>
              context.log.error(s"Failed to download file: $uri")
              replyTo ! StatusReply.error(exception)
          end match

        case UploadFile(uri, replyTo) =>
          context.log.info(s"Uploading file:")
          replyTo ! StatusReply.Ack

      end match
      Behaviors.stopped
    }
  end processing

  def downloadFile(uri: Uri): Try[Seq[Byte]] =
    val client = FTPClient()
    val bytes = Try {
      client.connect("localhost", 21)
      client.login("one", "123")
      client.enterLocalPassiveMode()
      client.setFileType(FTP.BINARY_FILE_TYPE)
      val file =
        client.retrieveFileStream(uri.value)
      val bytes = file.readAllBytes()
      file.close()
      bytes.toSeq
    }
    client.completePendingCommand()
    client.logout()
    client.disconnect()
    bytes

  end downloadFile

  def uploadFile(uri: Uri, file: Seq[Byte]): Try[Boolean] =
    val client = FTPClient()
    val result = Try {
      client.connect("localhost", 21)
      client.login("one", "123")
      client.enterLocalPassiveMode()
      client.setFileType(FTP.BINARY_FILE_TYPE)
      client.storeFile(uri.value, java.io.ByteArrayInputStream(file.toArray))
    }
    client.completePendingCommand()
    client.logout()
    client.disconnect()
    result
  end uploadFile

  def saveFile(path: Path, file: Seq[Byte]): Unit =
    os.write(path, file.toArray)
  end saveFile

  def loadFile(path: Path): Seq[Byte] =
    os.read.bytes(path).toSeq
  end loadFile
end RemoteFileWorker

package actors.files

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import os.Path
import os.RelPath
import types.OpaqueTypes.RemoteStorageHost
import types.OpaqueTypes.RemoteStoragePassword
import types.OpaqueTypes.RemoteStoragePort
import types.OpaqueTypes.RemoteStorageUser

import scala.util.Failure
import scala.util.Success
import scala.util.Try

object RemoteFileWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class DownloadFile(
      remoteStorageHost: RemoteStorageHost,
      remoteStoragePort: RemoteStoragePort,
      remoteStorageUser: RemoteStorageUser,
      remoteStoragePass: RemoteStoragePassword,
      path: RelPath,
      replyTo: ActorRef[StatusReply[Path]]
  ) extends Command
  final case class UploadFile(
      remoteStorageHost: RemoteStorageHost,
      remoteStoragePort: RemoteStoragePort,
      remoteStorageUser: RemoteStorageUser,
      remoteStoragePass: RemoteStoragePassword,
      path: RelPath,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  def apply(): Behavior[Command] = processing

  def processing: Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case DownloadFile(
              remoteStorageHost,
              remoteStoragePort,
              remoteStorageUser,
              remoteStoragePass,
              path,
              replyTo
            ) =>
          context.log.info(s"DownloadFile command received. Path --> $path.")

          downloadFile(path) match
            case Success(bytes) =>
              context.log.info(s"Download success. Path --> $path.")

              val localPath = os.pwd / path
              saveFile(localPath, bytes) match
                case Success(_) =>
                  context.log.info(s"File save success. Path --> $localPath.")
                  context.log.info(
                    s"Sending StatusReply.Success to RemoteFileManager. Path --> $localPath."
                  )

                  replyTo ! StatusReply.Success(localPath)
                case Failure(exception) =>
                  context.log.error(
                    s"File save failed. Path --> $localPath. Exception thrown: ${exception.getMessage()}."
                  )
                  context.log.info(
                    s"Sending StatusReply.Error to RemoteFileManager."
                  )
                  replyTo ! StatusReply.Error(exception)
              end match
            case Failure(exception) =>
              context.log.error(
                s"Download failed. Path --> $path. Exception thrown: ${exception.getMessage()}."
              )
              context.log.info(
                s"Sending StatusReply.Error to RemoteFileManager. Path --> $path."
              )

              replyTo ! StatusReply.Error(exception)
          end match

        case UploadFile(
              remoteStorageHost,
              remoteStoragePort,
              remoteStorageUser,
              remoteStoragePass,
              path,
              replyTo
            ) =>
          context.log.info(s"UploadFile command received. Path --> $path.")

          val localPath = os.pwd / path
          loadFile(localPath) match
            case Success(bytes) =>
              context.log.info(s"Local file load success. Path --> $localPath.")

              uploadFile(path, bytes) match
                case Success(_) =>
                  context.log.info(s"File upload success. Path --> $path.")

                  deleteFile(localPath) match
                    case Success(_) =>
                      context.log.info(
                        s"Local file delete success. Path --> $localPath."
                      )
                    case Failure(exception) =>
                      context.log.error(
                        s"Local file delete failed. Path --> $localPath. Exception thrown: ${exception.getMessage()}."
                      )
                  end match

                  context.log.info(
                    s"Sending StatusReply.Ack to RemoteFileManager. Path --> $path."
                  )
                  replyTo ! StatusReply.Ack
                case Failure(exception) =>
                  context.log.error(
                    s"RemoteFileWorker failed to upload file: $path. Exception thrown: ${exception.getMessage()}. Notifying RemoteFileManager..."
                  )
                  replyTo ! StatusReply.Error(exception)
              end match
            case Failure(exception) =>
              context.log.error(
                s"Local file load failed. Path --> $localPath. Exception thrown: ${exception.getMessage()}."
              )
              context.log.info(
                s"Sending StatusReply.Error to RemoteFileManager. Path --> $path."
              )
              replyTo ! StatusReply.Error(exception)
          end match

      end match
      Behaviors.stopped
    }
  end processing

  def downloadFile(path: RelPath): Try[Seq[Byte]] =
    val client = FTPClient()
    val bytes = Try {
      client.connect("localhost", 21)
      client.login("one", "123")
      client.enterLocalPassiveMode()
      client.setFileType(FTP.BINARY_FILE_TYPE)
      val file =
        client.retrieveFileStream(path.toString)
      val bytes = file.readAllBytes()
      file.close()
      bytes.toSeq
    }
    // client.completePendingCommand()
    client.logout()
    client.disconnect()
    bytes

  end downloadFile

  def uploadFile(path: RelPath, file: Seq[Byte]): Try[Boolean] =
    val client = FTPClient()
    val result = Try {
      client.connect("localhost", 21)
      client.login("one", "123")
      client.enterLocalPassiveMode()
      client.setFileType(FTP.BINARY_FILE_TYPE)
      client.storeFile(
        path.toString,
        java.io.ByteArrayInputStream(file.toArray)
      )
    }
    // client.completePendingCommand()
    client.logout()
    client.disconnect()
    result
  end uploadFile

  def saveFile(path: Path, file: Seq[Byte]): Try[Unit] =
    Try(os.write.over(path, file.toArray, createFolders = true))
  end saveFile

  def loadFile(path: Path): Try[Seq[Byte]] =
    Try(os.read.bytes(path).toSeq)
  end loadFile

  def deleteFile(path: Path): Try[Unit] =
    Try(os.remove.all(path))
  end deleteFile
end RemoteFileWorker

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
import types.OpaqueTypes.RemoteStorageHost
import types.OpaqueTypes.RemoteStoragePort
import types.OpaqueTypes.RemoteStorageUser
import types.OpaqueTypes.RemoteStoragePassword
import os.RelPath

private val DefaultRemoteOpsRetries = 5

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
      replyTo: ActorRef[StatusReply[Path]],
      retries: Int = DefaultRemoteOpsRetries
  ) extends Command
  final case class UploadFile(
      remoteStorageHost: RemoteStorageHost,
      remoteStoragePort: RemoteStoragePort,
      remoteStorageUser: RemoteStorageUser,
      remoteStoragePass: RemoteStoragePassword,
      path: RelPath,
      replyTo: ActorRef[StatusReply[Done]],
      retries: Int = DefaultRemoteOpsRetries
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
              replyTo,
              retries
            ) =>
          context.log.info(
            s"RemoteFileWorker received download request for path: $path. Attempting to download..."
          )
          downloadFile(path) match
            case Success(bytes) =>
              context.log.info(
                s"RemoteFileWorker successfully downloaded file: $path. Attempting to save..."
              )
              val localPath = os.pwd / path
              saveFile(localPath, bytes) match
                case Success(_) =>
                  context.log.info(
                    s"RemoteFileWorker successfully saved file: $localPath. Notifiying RemoteFileManager..."
                  )
                  replyTo ! StatusReply.success(localPath)
                case Failure(exception) =>
                  context.log.error(
                    s"RemoteFileWorker failed to save file: $localPath. Notifying RemoteFileManager..."
                  )
                  replyTo ! StatusReply.error(exception)
              end match
            case Failure(exception) =>
              if retries > 0 then
                context.log.error(
                  s"RemoteFileWorker failed to download file: $path. Retrying..."
                )
                context.self ! DownloadFile(
                  remoteStorageHost,
                  remoteStoragePort,
                  remoteStorageUser,
                  remoteStoragePass,
                  path,
                  replyTo,
                  retries - 1
                )
              else
                context.log.error(
                  s"RemoteFileWorker failed to download file: $path. Retries exhausted."
                )
                replyTo ! StatusReply.error(exception)
          end match

        case UploadFile(
              remoteStorageHost,
              remoteStoragePort,
              remoteStorageUser,
              remoteStoragePass,
              path,
              replyTo,
              retries
            ) =>
          context.log.info(
            s"RemoteFileWorker received upload request for path: $path. Attempting to upload..."
          )
          // replyTo ! StatusReply.Ack
          val localPath = os.pwd / path
          loadFile(localPath) match
            case Success(bytes) =>
              context.log.info(
                s"RemoteFileWorker successfully loaded file: $localPath. Attempting to upload..."
              )

              uploadFile(localPath, bytes) match
                case Success(_) =>
                  context.log.info(
                    s"RemoteFileWorker successfully uploaded file: $path. Notifying RemoteFileManager..."
                  )
                  replyTo ! StatusReply.success(Done)
                case Failure(exception) =>
                  if retries > 0 then
                    context.log.error(
                      s"RemoteFileWorker failed to upload file: $path. Retrying..."
                    )
                    context.self ! UploadFile(
                      remoteStorageHost,
                      remoteStoragePort,
                      remoteStorageUser,
                      remoteStoragePass,
                      path,
                      replyTo,
                      retries - 1
                    )
                  else
                    context.log.error(
                      s"RemoteFileWorker failed to upload file: $path. Retries exhausted."
                    )
                    replyTo ! StatusReply.error(exception)
              end match
            case Failure(exception) =>
              context.log.error(
                s"RemoteFileWorker failed to load file: $localPath. Notifying RemoteFileManager..."
              )
              replyTo ! StatusReply.error(exception)
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
    client.completePendingCommand()
    client.logout()
    client.disconnect()
    bytes

  end downloadFile

  def uploadFile(path: Path, file: Seq[Byte]): Try[Boolean] =
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
    client.completePendingCommand()
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
end RemoteFileWorker

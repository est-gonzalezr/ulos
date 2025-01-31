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
import types.Task
import utilities.FileSystem

import scala.util.Failure
import scala.util.Success
import scala.util.Try

object RemoteFileWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class DownloadFiles(
      remoteStorageHost: RemoteStorageHost,
      remoteStoragePort: RemoteStoragePort,
      remoteStorageUser: RemoteStorageUser,
      remoteStoragePass: RemoteStoragePassword,
      task: Task,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class UploadFiles(
      remoteStorageHost: RemoteStorageHost,
      remoteStoragePort: RemoteStoragePort,
      remoteStorageUser: RemoteStorageUser,
      remoteStoragePass: RemoteStoragePassword,
      task: Task,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  def apply(): Behavior[Command] = processing

  def processing: Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case DownloadFiles(
              remoteStorageHost,
              remoteStoragePort,
              remoteStorageUser,
              remoteStoragePass,
              task,
              replyTo
            ) =>
          context.log.info(s"DownloadFile command received. Task --> $task.")
          val filesRelPath = task.filePath
          val containerRelPath = task.containerImagesPaths.head

          (downloadFile(filesRelPath), downloadFile(containerRelPath)) match
            case (Success(fileBytes), Success(containerBytes)) =>
              context.log.info(
                s"Dual download success. FileRelPath --> $filesRelPath. ContainerRelPath --> $containerRelPath."
              )

              (
                FileSystem.saveFile(task.filePath, fileBytes),
                FileSystem.saveFile(containerRelPath, containerBytes)
              ) match
                case (Success(_), Success(_)) =>
                  context.log.info(
                    s"File save success. FileRelPath --> $filesRelPath."
                  )
                  context.log.info(
                    s"Container save success. ContainerRelPath --> $containerRelPath."
                  )
                  context.log.info(
                    s"Sending StatusReply.Ack to RemoteFileManager."
                  )

                  replyTo ! StatusReply.Ack

                case (Failure(exception), _) =>
                  context.log.error(
                    s"File save failed. FileRelPath --> $filesRelPath. Exception thrown: ${exception.getMessage()}."
                  )
                  context.log.info(
                    s"Sending StatusReply.Error to RemoteFileManager."
                  )

                  replyTo ! StatusReply.Error(exception)

                case (_, Failure(exception)) =>
                  context.log.error(
                    s"Container save failed. ContainerRelPath --> $filesRelPath. Exception thrown: ${exception.getMessage()}."
                  )
                  context.log.info(
                    s"Sending StatusReply.Error to RemoteFileManager."
                  )

                  replyTo ! StatusReply.Error(exception)
              end match

            case (Failure(exception), _) =>
              context.log.error(
                s"File download failed. FileRelPath --> $filesRelPath. Exception thrown: ${exception.getMessage()}."
              )
              context.log.info(
                s"Sending StatusReply.Error to RemoteFileManager."
              )

              replyTo ! StatusReply.Error(exception)

            case (_, Failure(exception)) =>
              context.log.error(
                s"Container download failed. ContainerRelPath --> $filesRelPath. Exception thrown: ${exception.getMessage()}."
              )
              context.log.info(
                s"Sending StatusReply.Error to RemoteFileManager."
              )

              replyTo ! StatusReply.Error(exception)
          end match

        case UploadFiles(
              remoteStorageHost,
              remoteStoragePort,
              remoteStorageUser,
              remoteStoragePass,
              task,
              replyTo
            ) =>
          context.log.info(
            s"UploadFile command received. Task --> $task."
          )
          val filesRelPath = task.filePath

          FileSystem.loadFile(filesRelPath) match
            case Success(bytes) =>
              context.log.info(
                s"Local file load success. FileRelPath --> $filesRelPath."
              )

              uploadFile(filesRelPath, bytes) match
                case Success(_) =>
                  context.log.info(
                    s"File upload success. FileRelPath --> $filesRelPath."
                  )

                  FileSystem.deleteFile(filesRelPath) match
                    case Success(_) =>
                      context.log.info(
                        s"Local file delete success. FileRelPath --> $filesRelPath."
                      )
                    case Failure(exception) =>
                      context.log.error(
                        s"Local file delete failed. FileRelPath --> $filesRelPath. Exception thrown: ${exception.getMessage()}."
                      )
                  end match

                  context.log.info(
                    s"Sending StatusReply.Ack to RemoteFileManager. FileRelPath --> $filesRelPath."
                  )

                  replyTo ! StatusReply.Ack

                case Failure(exception) =>
                  context.log.error(
                    s"RemoteFileWorker failed to upload file: $filesRelPath. Exception thrown: ${exception.getMessage()}. Notifying RemoteFileManager..."
                  )

                  replyTo ! StatusReply.Error(exception)

              end match
            case Failure(exception) =>
              context.log.error(
                s"Local file load failed. Path --> $filesRelPath. Exception thrown: ${exception.getMessage()}."
              )
              context.log.info(
                s"Sending StatusReply.Error to RemoteFileManager. Path --> $filesRelPath."
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
end RemoteFileWorker

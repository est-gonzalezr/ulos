package actors.files

/** @author
  *   Esteban Gonzalez Ruales
  */

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import os.Path
import types.OpaqueTypes.RemoteStorageHost
import types.OpaqueTypes.RemoteStoragePassword
import types.OpaqueTypes.RemoteStoragePort
import types.OpaqueTypes.RemoteStorageUser
import types.Task
import utilities.FileSystemUtil

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
          // context.log.info(s"DownloadFile command received. Task --> $task.")
          val filesPath = task.filePath

          downloadFile(filesPath) match
            case Success(fileBytes) =>
              // context.log.info(
              //   s"Dual download success. FilePath --> $filesPath. ContainerPath --> $containerPath."
              // )
              FileSystemUtil.saveFile(task.relTaskFilePath, fileBytes) match
                case Success(_) =>
                  // context.log.info(
                  //   s"File save success. FilePath --> $filesPath."
                  // )
                  // context.log.info(
                  //   s"Container save success. ContainerPath --> $containerPath."
                  // )
                  // context.log.info(
                  //   s"Sending StatusReply.Ack to RemoteFileManager."
                  // )

                  replyTo ! StatusReply.Ack

                case Failure(exception) =>
                  // context.log.error(
                  //   s"File save failed. FilePath --> $filesPath. Exception thrown: ${exception.getMessage()}."
                  // )
                  // context.log.info(
                  //   s"Sending StatusReply.Error to RemoteFileManager."
                  // )

                  replyTo ! StatusReply.Error(exception)
              end match

            case Failure(exception) =>
              // context.log.error(
              //   s"File download failed. FilePath --> $filesPath. Exception thrown: ${exception.getMessage()}."
              // )
              // context.log.info(
              //   s"Sending StatusReply.Error to RemoteFileManager."
              // )

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
          val filesPath = task.filePath

          FileSystemUtil.loadFile(task.relTaskFilePath) match
            case Success(bytes) =>
              // context.log.info(
              //   s"Local file load success. FilePath --> $filesPath."
              // )

              uploadFile(filesPath, bytes) match
                case Success(_) =>
                  // context.log.info(
                  //   s"File upload success. FilePath --> $filesPath."
                  // )

                  val results = Seq(
                    FileSystemUtil.deleteTaskBaseDir(task.relTaskFilePath),
                    FileSystemUtil.deleteFile(task.relTaskFilePath)
                  )

                  results.collectFirst { case Failure(exception) =>
                    exception
                  } match
                    case Some(exception) =>
                      // Handle the first failure (log it, throw it, etc.)
                      context.log.error(
                        s"RemoteFileWorker failed delete operation. Exception thrown: ${exception.getMessage()}"
                      )
                    case None =>
                    // All succeeded

                  end match

                  context.log.info(
                    s"Sending StatusReply.Ack to RemoteFileManager. FilePath --> $filesPath."
                  )

                  replyTo ! StatusReply.Ack

                case Failure(exception) =>
                  // context.log.error(
                  //   s"RemoteFileWorker failed to upload file: $filesPath. Exception thrown: ${exception.getMessage()}. Notifying RemoteFileManager..."
                  // )

                  replyTo ! StatusReply.Error(exception)

              end match
            case Failure(exception) =>
              // context.log.error(
              //   s"Local file load failed. Path --> $filesPath. Exception thrown: ${exception.getMessage()}."
              // )
              // context.log.info(
              //   s"Sending StatusReply.Error to RemoteFileManager. Path --> $filesPath."
              // )
              replyTo ! StatusReply.Error(exception)

          end match
      end match

      Behaviors.stopped
    }
  end processing

  def downloadFile(path: Path): Try[Seq[Byte]] =
    val client = FTPClient()
    // client.addProtocolCommandListener(
    //   PrintCommandListener(PrintWriter(System.out), true)
    // )
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
    // client.completePendingCommand()
    client.logout()
    client.disconnect()
    result
  end uploadFile
end RemoteFileWorker

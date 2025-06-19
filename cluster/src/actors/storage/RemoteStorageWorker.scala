package actors.storage

import scala.util.Failure
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
import types.OpaqueTypes.RemoteStorageUsername
import types.RemoteStorageConnectionParams
import types.Task
import utilities.FileSystemUtil

/** A stateless actor responsible for managing remote storage operations. A new
  * instance is created for each request.
  */
object RemoteStorageWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class DownloadFiles(
      task: Task,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class UploadFiles(
      task: Task,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  def apply(connParams: RemoteStorageConnectionParams): Behavior[Command] =
    manipulate(connParams)

  /** Handles the manipulation of files on remote storage.
    * @param connParams
    *   The connection parameters for the remote storage.
    *
    * @return
    *   A Behavior that handles the manipulation of files on remote storage.
    */
  private def manipulate(
      connParams: RemoteStorageConnectionParams
  ): Behavior[Command] =
    Behaviors.receive { (_, message) =>
      message match
        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case DownloadFiles(
              task,
              replyTo
            ) =>

          val _ = handleFileDownload(
            connParams,
            task,
            replyTo
          )
          Behaviors.stopped

        case UploadFiles(
              task,
              replyTo
            ) =>

          val _ = handleFileUpload(
            connParams,
            task,
            replyTo
          )
          Behaviors.stopped
    }
  end manipulate

  /** Handle the processes necessary to download a file.
    * @param connParams
    *   The connection parameters for the remote storage.
    * @param task
    *   The task containing the file path to download.
    * @param replyTo
    *   The actor to reply to with the status of the download.
    *
    * @return
    *   Unit
    */
  private def handleFileDownload(
      connParams: RemoteStorageConnectionParams,
      task: Task,
      replyTo: ActorRef[StatusReply[Done]]
  ): Unit =
    val filesPath = task.filePath

    downloadFile(
      connParams,
      filesPath
    ).fold(
      replyTo ! StatusReply.Error(_),
      fileBytes =>
        FileSystemUtil
          .saveFile(task.relTaskFilePath, fileBytes)
          .fold(
            replyTo ! StatusReply.Error(_),
            _ => replyTo ! StatusReply.Ack
          )
    )

  end handleFileDownload

  /** Handles the file upload task.
    *
    * @param connParams
    *   The connection parameters for the remote storage.
    * @param task
    *   The task containing the file path to upload.
    * @param replyTo
    *   The actor to reply to with the status of the upload.
    *
    * @return
    *   Unit
    */
  private def handleFileUpload(
      connParams: RemoteStorageConnectionParams,
      task: Task,
      replyTo: ActorRef[StatusReply[Done]]
  ): Unit =
    val filesPath = task.filePath

    FileSystemUtil
      .loadFile(task.relTaskFilePath)
      .fold(
        replyTo ! StatusReply.Error(_),
        bytes =>
          uploadFile(
            connParams,
            filesPath,
            bytes
          ).fold(
            replyTo ! StatusReply.Error(_),
            _ =>
              val _ = Seq(
                FileSystemUtil.deleteTaskBaseDir(task.relTaskFilePath),
                FileSystemUtil.deleteFile(task.relTaskFilePath)
              ).collectFirst { case Failure(th) =>
                println(th)
              }
              replyTo ! StatusReply.Ack
          )
      )
  end handleFileUpload

  /** Handles the file download task.
    *
    * @param connParams
    *   The connection parameters for the remote storage.
    * @param path
    *   The path of the file to download.
    * @return
    *   A Try containing the downloaded file as a sequence of bytes.
    */
  private def downloadFile(
      connParams: RemoteStorageConnectionParams,
      path: Path
  ): Try[Seq[Byte]] =
    val client = FTPClient()
    val bytes = Try {
      setFtpClient(connParams, client)
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

  /** Handles the file upload task.
    *
    * @param connParams
    *   The connection parameters for the remote storage.
    * @param path
    *   The path of the file to upload.
    * @param file
    *   The file to upload.
    * @return
    *   A Try containing a boolean indicating whether the upload was successful.
    */
  private def uploadFile(
      connParams: RemoteStorageConnectionParams,
      path: Path,
      file: Seq[Byte]
  ): Try[Boolean] =
    val client = FTPClient()
    val result = Try {
      setFtpClient(connParams, client)
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

  private def setFtpClient(
      connParams: RemoteStorageConnectionParams,
      client: FTPClient
  ): Unit =
    client.connect(connParams.host.value, connParams.port.value)
    client.login(connParams.username.value, connParams.password.value)
    client.enterLocalPassiveMode()
    client.setFileType(FTP.BINARY_FILE_TYPE)
    ()
  end setFtpClient

end RemoteStorageWorker

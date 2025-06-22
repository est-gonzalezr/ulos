package actors.storage

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
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
      replyTo: ActorRef[Response]
  ) extends Command
  final case class UploadFiles(
      task: Task,
      replyTo: ActorRef[Response]
  ) extends Command

  // Response protocol
  sealed trait Response

  final case class TaskDownloaded(task: Task) extends Response
  final case class TaskUploaded(task: Task) extends Response

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

        case DownloadFiles(task, replyTo) =>
          val filesPath = task.filePath

          val bytes = downloadFile(connParams, filesPath)
          val _ = FileSystemUtil.saveFile(task.relTaskFilePath, bytes)

          replyTo ! TaskDownloaded(task)

          Behaviors.stopped

        case UploadFiles(task, replyTo) =>
          val bytes = FileSystemUtil.loadFile(task.relTaskFilePath)
          uploadFile(connParams, task.filePath, bytes)

          val _ = FileSystemUtil.deleteTaskBaseDir(task.relTaskFilePath)
          val _ = FileSystemUtil.deleteFile(task.relTaskFilePath)

          replyTo ! TaskUploaded(task)

          Behaviors.stopped
    }
  end manipulate

  /** Handles the file download task.
    *
    * @param connParams
    *   The connection parameters for the remote storage.
    * @param path
    *   The path of the file to download.
    * @return
    *   The downloaded file as a sequence of bytes.
    */
  private def downloadFile(
      connParams: RemoteStorageConnectionParams,
      path: Path
  ): Seq[Byte] =
    val client = FTPClient()
    setFtpClient(connParams, client)

    val file = client.retrieveFileStream(path.toString)
    val bytes = file.readAllBytes().toSeq
    file.close()

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
    *   A boolean indicating whether the upload was successful.
    */
  private def uploadFile(
      connParams: RemoteStorageConnectionParams,
      path: Path,
      file: Seq[Byte]
  ): Unit =
    val client = FTPClient()
    setFtpClient(connParams, client)

    val result = client.storeFile(
      path.toString,
      java.io.ByteArrayInputStream(file.toArray)
    )

    if !result then throw new RuntimeException("Failed to upload file")
    end if

    // client.completePendingCommand()
    client.logout()
    client.disconnect()
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

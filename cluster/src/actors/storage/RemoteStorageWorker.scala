package actors.storage

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import os.Path
import os.RelPath
import types.OpaqueTypes.RemoteStorageHost
import types.OpaqueTypes.RemoteStoragePassword
import types.OpaqueTypes.RemoteStoragePort
import types.OpaqueTypes.RemoteStorageUsername
import types.RemoteStorageConfiguration
import types.Task

/** A stateless actor responsible for managing remote storage operations. A new
  * instance is created for each request.
  */
object RemoteStorageWorker:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class DownloadFiles(
      task: Task
  ) extends Command
  final case class UploadFiles(
      task: Task
  ) extends Command
  final case class DeleteFiles(
      task: Task
  ) extends Command

  // Response protocol
  sealed trait Response

  final case class TaskDownloaded(task: Task) extends Response
  final case class TaskUploaded(task: Task) extends Response
  final case class TaskDeleted(task: Task) extends Response

  def apply(
      connParams: RemoteStorageConfiguration,
      replyTo: ActorRef[Response]
  ): Behavior[Command] =
    manipulate(connParams, replyTo)

  /** Handles the manipulation of files on remote storage.
    * @param connParams
    *   The connection parameters for the remote storage.
    *
    * @return
    *   A Behavior that handles the manipulation of files on remote storage.
    */
  private def manipulate(
      connParams: RemoteStorageConfiguration,
      replyTo: ActorRef[Response]
  ): Behavior[Command] =
    Behaviors.receive { (_, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case DownloadFiles(task) =>
          val filesPath = task.filePath

          val bytes = downloadFile(connParams, filesPath)
          os.write.over(
            os.pwd / task.relTaskFilePath,
            bytes.toArray,
            createFolders = true
          )

          replyTo ! TaskDownloaded(task)

          Behaviors.stopped

        case UploadFiles(task) =>
          val bytes = loadFile(task.relTaskFilePath)
          uploadFile(connParams, task.filePath, bytes)

          replyTo ! TaskUploaded(task)

          Behaviors.stopped

        case DeleteFiles(task) =>
          val _ = deleteTaskBaseDir(task.relTaskFilePath)
          val _ = deleteFile(task.relTaskFilePath)

          replyTo ! TaskDeleted(task)

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
      connParams: RemoteStorageConfiguration,
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
      connParams: RemoteStorageConfiguration,
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
      connParams: RemoteStorageConfiguration,
      client: FTPClient
  ): Unit =
    client.connect(connParams.host.value, connParams.port.value)
    client.login(connParams.username.value, connParams.password.value)
    client.enterLocalPassiveMode()
    client.setFileType(FTP.BINARY_FILE_TYPE)
    ()
  end setFtpClient

  private def deleteTaskBaseDir(relPath: RelPath): Path =
    val absPath = os.pwd / relPath
    val deletePath = absPath / os.up / absPath.baseName
    os.remove.all(deletePath)
    absPath
  end deleteTaskBaseDir

  private def deleteFile(relPath: RelPath): Path =
    val absPath = os.pwd / relPath
    if os.remove(absPath) then absPath
    else throw Throwable("File doesn't exist")
    end if
  end deleteFile

  private def loadFile(relPath: RelPath): Seq[Byte] =
    val absPath = os.pwd / relPath
    os.read.bytes(absPath).toSeq
  end loadFile

end RemoteStorageWorker

/** @author
  *   Esteban Gonzalez Ruales
  */

import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient

/** The FtpStorage case class provides a storage system to interact with an FTP
  * server.
  *
  * @param host
  *   the host of the FTP server
  * @param port
  *   the port of the FTP server
  * @param user
  *   the user of the FTP server
  * @param pass
  *   the password of the FTP server
  */
case class FtpStorage(host: String, port: Int, user: String, pass: String)
    extends Storage:

  /** The ftpConnection method provides a resource to connect to an FTP server.
    *
    * @return
    *   a Resource monad with an FTPClient
    */
  private def ftpConnection: Resource[IO, FTPClient] = Resource.make(IO.delay {
    val client = FTPClient()
    client.connect(this.host, this.port)
    client.login(this.user, this.pass)
    client.enterLocalPassiveMode()
    client.setFileType(FTP.BINARY_FILE_TYPE)
    client
  })(client =>
    IO.delay {
      client.logout()
      client.disconnect()
    }
  )

  /** The uploadFile method uploads a file to the storage system.
    *
    * @param file
    *   the file to upload
    * @param path
    *   the path to upload the file
    *
    * @return
    *   an IO monad with a boolean indicating if the file was uploaded
    */
  def uploadFile(file: Seq[Byte], path: String): IO[Boolean] =
    ftpConnection.use(client =>
      IO.delay {
        val result =
          client.storeFile(path, java.io.ByteArrayInputStream(file.toArray))
        client.completePendingCommand()
        result
      }
    )

  /** The downloadFile method downloads a file from the storage system.
    *
    * @param path
    *   the path of the file to download
    *
    * @return
    *   an IO monad with the bytes of the file
    */
  def downloadFile(path: String): IO[Seq[Byte]] =
    ftpConnection.use(client =>
      val file =
        downloadStream(client, path).use(stream =>
          IO.delay(stream.readAllBytes().toSeq)
        )
      client.completePendingCommand()
      file
    )

  /** The deleteFile method deletes a file from the storage system.
    *
    * @param path
    *   the path of the file to delete
    *
    * @return
    *   an IO monad with a boolean indicating if the file was deleted
    */
  def deleteFile(path: String): IO[Boolean] =
    ftpConnection.use(client =>
      IO.delay {
        val result = client.deleteFile(path)
        client.completePendingCommand()
        result
      }
    )

  private def downloadStream(
      client: FTPClient,
      path: String
  ): Resource[IO, java.io.InputStream] =
    Resource.make(IO.delay(client.retrieveFileStream(path)))(stream =>
      IO.delay(stream.close())
    )

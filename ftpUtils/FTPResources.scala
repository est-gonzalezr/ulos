/** @author
  *   Esteban Gonzalez Ruales
  */

import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient

/** The FTPResources object provides resources to interact with an FTP server.
  */
object FTPResources:

  /** The ftpConnection method provides a resource to connect to an FTP server.
    *
    * @param host
    *   the host of the FTP server
    * @param port
    *   the port of the FTP server
    * @param user
    *   the user of the FTP server
    * @param pass
    *   the password of the FTP server
    *
    * @return
    *   a Resource monad with an FTPClient
    */
  def ftpConnection(
      host: String,
      port: Int,
      user: String,
      pass: String
  ): Resource[IO, FTPClient] = Resource.make(IO.delay {
    val client = FTPClient()
    client.connect(host, port)
    client.login(user, pass)
    client.enterLocalPassiveMode()
    client.setFileType(FTP.BINARY_FILE_TYPE)
    client
  })(client =>
    IO.delay {
      client.logout()
      client.disconnect()
    }
  )

  /** The bytesFromServerFilepath method retrieves the bytes from a file in the
    * server.
    *
    * @param client
    *   the FTP client
    * @param path
    *   the path of the file in the server
    *
    * @return
    *   an IO monad with the bytes of the file
    */
  def bytesFromServerFilepath(
      client: FTPClient,
      path: String
  ): IO[Option[Array[Byte]]] =
    inputStreamFromServerFilepath(client, path).use(stream =>
      IO.delay(
        Option(stream).map(_.readAllBytes())
      )
    )

  /** The bytesToServerFilepath method stores the bytes in a file in the server.
    *
    * @param client
    *   the FTP client
    * @param path
    *   the path of the file in the server
    * @param bytes
    *   the bytes to store in the file
    *
    * @return
    *   an IO monad with a boolean indicating if the operation was successful
    */
  def bytesToServerFilepath(
      client: FTPClient,
      path: String,
      bytes: Array[Byte]
  ): IO[Boolean] =
    inputStreamToServerFilepath(client, bytes).use(stream =>
      IO.delay(
        client.storeFile(path, stream)
      )
    )

  /** The deleteFromServerFilepath method deletes a file in the server.
    *
    * @param client
    *   the FTP client
    * @param path
    *   the path of the file in the server
    *
    * @return
    *   an IO monad with a boolean indicating if the operation was successful
    */
  def deleteFromServerFilepath(
      client: FTPClient,
      path: String
  ): IO[Boolean] =
    val resource = Resource.make(IO.delay {
      client.deleteFile(path)
    })(_ =>
      IO.delay {
        client.completePendingCommand()
      }
    )

    resource.use(IO.pure)

  /** The inputStreamFromServerFilepath method provides a resource to retrieve
    * an input stream from a file in the server.
    *
    * @param client
    *   the FTP client
    * @param path
    *   the path of the file in the server
    *
    * @return
    *   a Resource monad with an input stream
    */
  private def inputStreamFromServerFilepath(
      client: FTPClient,
      path: String
  ): Resource[IO, java.io.InputStream] =
    Resource.make(IO.delay {
      client.retrieveFileStream(path)
    })(stream =>
      IO.delay {
        stream.close()
        client.completePendingCommand()
      }
    )

  /** The inputStreamToServerFilepath method provides a resource to store an
    * input stream in a file in the server.
    *
    * @param client
    *   the FTP client
    * @param bytes
    *   the bytes to store in the file
    *
    * @return
    *   a Resource monad with an input stream
    */
  private def inputStreamToServerFilepath(
      client: FTPClient,
      bytes: Array[Byte]
  ): Resource[IO, java.io.InputStream] =
    Resource.make(IO.delay {
      java.io.ByteArrayInputStream(bytes)
    })(stream =>
      IO.delay {
        stream.close()
        client.completePendingCommand()
      }
    )

/** @author
  *   Esteban Gonzalez Ruales
  */

import cats.effect.IO
import cats.effect.kernel.Resource
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient

object FTPResources:
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

  def bytesFromServerFilepath(
      client: FTPClient,
      path: String
  ): IO[Option[Array[Byte]]] =
    inputStreamFromServerFilepath(client, path).use(stream =>
      IO.delay(
        Option(stream).map(_.readAllBytes())
      )
    )

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

/** @author
  *   Esteban Gonzalez Ruales
  */

trait Storage:
  def uploadFile(file: File, path: String): IO[Unit]

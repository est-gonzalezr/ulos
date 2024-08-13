/** @author
  *   Esteban Gonzalez Ruales
  */

import cats.effect.IO

/** The Storage trait provides base methods to interact with a storage system.
  */
trait Storage:
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
  def uploadFile(file: Seq[Byte], path: String): IO[Boolean]

  /** The downloadFile method downloads a file from the storage system.
    *
    * @param path
    *   the path of the file to download
    *
    * @return
    *   an IO monad with the bytes of the file
    */
  def downloadFile(path: String): IO[Seq[Byte]]

  /** The deleteFile method deletes a file from the storage system.
    *
    * @param path
    *   the path of the file to delete
    *
    * @return
    *   an IO monad with a boolean indicating if the file was deleted
    */
  def deleteFile(path: String): IO[Boolean]

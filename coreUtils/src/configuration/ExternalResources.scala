/** @author
  *   Esteban Gonzalez Ruales
  */

package configuration

import cats.effect.IO
import cats.effect.kernel.Resource
import os.Path

/** Provides utility functions to use external resources.
  */
case object ExternalResources:

  /** Reads the environment variables and returns them as a map.
    *
    * @return
    *   A Resource monad with the environment variables as a map
    */
  def environmentVariableMap: Resource[IO, Map[String, String]] =
    Resource.make(IO.delay(sys.env))(_ => IO.unit)

  /** Reads a file and returns its content as a string.
    *
    * @param absPath
    *   The absolute path of the file
    *
    * @return
    *   A Resource monad with the content of the file as a string
    */
  def stringFromFilepath(absPath: Path): Resource[IO, String] =
    Resource.make(IO.delay(os.read(absPath)))(_ => IO.unit)

  /** Reads a file and returns its content as a byte array.
    *
    * @param absPath
    *   The absolute path of the file
    *
    * @return
    *   A Resource monad with the content of the file as a byte array
    */
  def bytesFromFilepath(absPath: Path): Resource[IO, Seq[Byte]] =
    Resource.make(IO.delay(os.read.bytes(absPath).toSeq))(_ => IO.unit)

  /** Reads a file and returns its content as a sequence of strings.
    *
    * @param absPath
    *   The absolute path of the file
    *
    * @return
    *   A Resource monad with the content of the file as a sequence of strings
    */
  def linesFromFilepath(absPath: Path): Resource[IO, Seq[String]] =
    Resource.make(IO.delay(os.read.lines(absPath)))(_ => IO.unit)
end ExternalResources

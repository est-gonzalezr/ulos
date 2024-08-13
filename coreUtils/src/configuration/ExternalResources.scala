/** @author
  *   Esteban Gonzalez Ruales
  */

package configuration

import cats.effect.IO
import cats.effect.kernel.Resource
import os.Path

/** The ExternalResources object provides utility functions to use external
  * resources.
  */
case object ExternalResources:

  /** The environmentVariableMap function reads the environment variables and
    * returns them as a map.
    *
    * @return
    *   a Resource monad with the environment variables as a map
    */
  def environmentVariableMap: Resource[IO, Map[String, String]] =
    Resource.make(IO.delay(sys.env))(_ => IO.unit)

  /** The stringFromFilepath function reads a file and returns its content as a
    * string.
    *
    * @param absPath
    *   the absolute path of the file
    *
    * @return
    *   a Resource monad with the content of the file as a string
    */
  def stringFromFilepath(absPath: Path): Resource[IO, String] =
    Resource.make(IO.delay(os.read(absPath)))(_ => IO.unit)

  /** The bytesFromFilepath function reads a file and returns its content as a
    * byte array.
    *
    * @param absPath
    *   the absolute path of the file
    *
    * @return
    *   a Resource monad with the content of the file as a byte array
    */
  def bytesFromFilepath(absPath: Path): Resource[IO, Seq[Byte]] =
    Resource.make(IO.delay(os.read.bytes(absPath).toSeq))(_ => IO.unit)

  /** The linesFromFilepath function reads a file and returns its content as a
    * sequence of strings.
    *
    * @param absPath
    *   the absolute path of the file
    *
    * @return
    *   a Resource monad with the content of the file as a sequence of strings
    */
  def linesFromFilepath(absPath: Path): Resource[IO, Seq[String]] =
    Resource.make(IO.delay(os.read.lines(absPath)))(_ => IO.unit)

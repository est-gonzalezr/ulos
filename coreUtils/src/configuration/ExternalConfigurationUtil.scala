/** @author
  *   Esteban Gonzalez Ruales
  */

package configuration

import cats.effect.IO
import os.Path

/** The ExternalConfigurationUtil object provides utility functions to read
  * environment variables and files in a functional way. The functions are
  * implemented using the IO monad from the Cats Effect library to abstract away
  * side effects.
  */
object ExternalConfigurationUtil:

  /** The environmentVariableMap function reads the environment variables and
    * returns them as a map.
    *
    * @return
    *   an IO monad with the environment variables as a map
    */
  def environmentVariableMap: IO[Map[String, String]] =
    IO.delay(sys.env)

  /** The stringFromFilepath function reads a file and returns its content as a
    * string.
    *
    * @param absPath
    *   the absolute path of the file
    *
    * @return
    *   an IO monad with the content of the file as a string
    */
  def stringFromFilepath(absPath: Path): IO[String] =
    IO.delay(os.read(absPath))

  /** The bytesFromFilepath function reads a file and returns its content as a
    * byte array.
    *
    * @param absPath
    *   the absolute path of the file
    *
    * @return
    *   an IO monad with the content of the file as a byte array
    */
  def bytesFromFilepath(absPath: Path): IO[Array[Byte]] =
    IO.delay(os.read.bytes(absPath))

  /** The linesFromFilepath function reads a file and returns its content as a
    * sequence of strings.
    *
    * @param absPath
    *   the absolute path of the file
    *
    * @return
    *   an IO monad with the content of the file as a sequence of strings
    */
  def linesFromFilepath(absPath: Path): IO[Seq[String]] =
    IO.delay(os.read.lines(absPath))

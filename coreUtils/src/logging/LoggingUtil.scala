/** @author
  *   Esteban Gonzalez Ruales
  */

package logging

import cats.effect.IO
import cats.effect.std.Console
import types.LogLevel
import types.LoggingColor.*

/** Utility object to print logs to the terminal */
case object LoggingUtil:
  /** Print a message to the terminal with relevant information
    *
    * @tparam T
    *   The type of the object which class is being used to log the message
    * @param logLevel
    *   The level of the log
    * @param module
    *   The class of the object that is logging the message
    * @param message
    *   The message to be logged
    *
    * @return
    *   An IO monad that represents the action of printing the message to the
    *   terminal
    */
  private def terminalLog[T](logLevel: LogLevel)(module: Class[T])(
      message: String
  ): IO[Unit] =
    val timestamp = java.time.Instant.now().toString

    val color = logLevel match
      case LogLevel.INFO    => Cyan.value
      case LogLevel.DEBUG   => Blue.value
      case LogLevel.WARN    => Yellow.value
      case LogLevel.ERROR   => Red.value
      case LogLevel.FATAL   => Magenta.value
      case LogLevel.SUCCESS => Green.value

    Console[IO].println(
      s"[$timestamp] [${color}$logLevel${Reset.value}] [${module.getName}] $message"
    )
  end terminalLog

  def terminalLogInfo[T](module: Class[T])(message: String): IO[Unit] =
    terminalLog(LogLevel.INFO)(module)(message)

  def terminalLogDebug[T](module: Class[T])(message: String): IO[Unit] =
    terminalLog(LogLevel.DEBUG)(module)(message)

  def terminalLogWarn[T](module: Class[T])(message: String): IO[Unit] =
    terminalLog(LogLevel.WARN)(module)(message)

  def terminalLogError[T](module: Class[T])(message: String): IO[Unit] =
    terminalLog(LogLevel.ERROR)(module)(message)

  def terminalLogFatal[T](module: Class[T])(message: String): IO[Unit] =
    terminalLog(LogLevel.FATAL)(module)(message)

  def terminalLogSuccess[T](module: Class[T])(message: String): IO[Unit] =
    terminalLog(LogLevel.SUCCESS)(module)(message)

end LoggingUtil

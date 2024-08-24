/** @author
  *
  * Esteban Gonzalez Ruales
  */

package logging

import types.LogLevel
import types.LoggingColor.*

/** Utility object to print logs to the terminal */
case object LoggingUtil:
  /** Print a message to the terminal with relevant information
    *
    * @tparam T
    *   The type of the object which class is being used to log the message
    * @param module
    *   The class of the object that is logging the message
    * @param logLevel
    *   The level of the log
    * @param message
    *   The message to be logged
    */
  def terminalLog[T](
      module: Class[T]
  )(logLevel: LogLevel)(message: String): Unit =
    val timestamp = java.time.Instant.now().toString

    val color = logLevel match
      case LogLevel.INFO    => Cyan.value
      case LogLevel.DEBUG   => Blue.value
      case LogLevel.WARN    => Yellow.value
      case LogLevel.ERROR   => Red.value
      case LogLevel.FATAL   => Magenta.value
      case LogLevel.SUCCESS => Green.value

    println(
      s"[$timestamp] [${color}$logLevel${Reset.value}] [${module.getName}] $message"
    )
  end terminalLog
end LoggingUtil

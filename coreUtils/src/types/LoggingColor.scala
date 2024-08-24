/** @author
  *   Esteban Gonzalez Ruales
  */
package types

/** Enumerates the colors that can be used to print logs to the terminal */
enum LoggingColor(val value: String):
  case Reset extends LoggingColor("\u001B[0m")
  case Red extends LoggingColor("\u001B[31m")
  case Green extends LoggingColor("\u001B[32m")
  case Yellow extends LoggingColor("\u001B[33m")
  case Blue extends LoggingColor("\u001B[34m")
  case Magenta extends LoggingColor("\u001B[35m")
  case Cyan extends LoggingColor("\u001B[36m")
end LoggingColor

/** @author
  *   Esteban Gonzalez Ruales
  */
package types

/** Enumerates the colors that can be used to print logs to the terminal */
enum TerminalLoggingColor(val value: String):
  case Reset extends TerminalLoggingColor("\u001B[0m")
  case Red extends TerminalLoggingColor("\u001B[31m")
  case Green extends TerminalLoggingColor("\u001B[32m")
  case Yellow extends TerminalLoggingColor("\u001B[33m")
  case Blue extends TerminalLoggingColor("\u001B[34m")
  case Magenta extends TerminalLoggingColor("\u001B[35m")
  case Cyan extends TerminalLoggingColor("\u001B[36m")
end TerminalLoggingColor

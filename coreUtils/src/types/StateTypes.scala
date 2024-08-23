/** @author
  *   Esteban Gonzalez Ruales
  */

package types

enum StateTypes(val value: String):
  case ParsingFailure extends StateTypes("ParsingFailure")
  case ParsingSuccess extends StateTypes("ParsingSuccess")
  case ExecutionFailure extends StateTypes("ExecutionFailure")
  case ExecutionSuccess extends StateTypes("ExecutionSuccess")
  case Error extends StateTypes("Error")
end StateTypes

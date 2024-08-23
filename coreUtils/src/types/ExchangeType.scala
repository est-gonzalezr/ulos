/** @author
  *   Esteban Gonzalez Ruales
  */

package types

/** Represents the exchange types for the messaging system.
  *
  * @param strValue
  *   The string value of the exchange type
  */
enum ExchangeType(val strValue: String):
  case Direct extends ExchangeType("direct")
  case Fanout extends ExchangeType("fanout")
  case Topic extends ExchangeType("topic")
  case Headers extends ExchangeType("headers")
end ExchangeType

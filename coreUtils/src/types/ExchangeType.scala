/** @author
  *   Esteban Gonzalez Ruales
  */

package types

/** The ExchangeType enum represents the exchange types for the messaging
  * system.
  *
  * @param strValue
  *   the string value of the exchange type
  */
enum ExchangeType(val strValue: String):
  case Direct extends ExchangeType("direct")
  case Fanout extends ExchangeType("fanout")
  case Topic extends ExchangeType("topic")
  case Headers extends ExchangeType("headers")

/** @author
  *   Esteban Gonzalez Ruales
  */

package types

import types.OpaqueTypes.ExchangeName

/** The BrokerExchange class represents an exchange in the broker.
  *
  * @param exchangeName
  *   the name of the exchange
  * @param exchangeType
  *   the type of the exchange
  * @param durable
  *   if the exchange is durable
  * @param autoDelete
  *   if the exchange is autoDelete
  * @param internal
  *   if the exchange is internal
  */
case class BrokerExchange(
    val exchangeName: ExchangeName,
    val exchangeType: ExchangeType,
    val durable: Boolean,
    val autoDelete: Boolean,
    val internal: Boolean
)

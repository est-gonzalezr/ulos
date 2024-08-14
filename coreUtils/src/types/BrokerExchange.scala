/** @author
  *   Esteban Gonzalez Ruales
  */

package types

import types.OpaqueTypes.ExchangeName

/** Represents an exchange in the broker.
  *
  * @param exchangeName
  *   The name of the exchange
  * @param exchangeType
  *   The type of the exchange
  * @param durable
  *   If the exchange is durable
  * @param autoDelete
  *   If the exchange is autoDelete
  * @param internal
  *   If the exchange is internal
  */
case class BrokerExchange(
    val exchangeName: ExchangeName,
    val exchangeType: ExchangeType,
    val durable: Boolean,
    val autoDelete: Boolean,
    val internal: Boolean
)

/** @author
  *   Esteban Gonzalez Ruales
  */

package types

import types.OpaqueTypes.ExchangeName

/** @param exchangeName
  * @param exchangeType
  * @param durable
  * @param autoDelete
  * @param internal
  * @param queues
  */
case class BrokerExchange(
    val exchangeName: ExchangeName,
    val exchangeType: ExchangeType,
    val durable: Boolean,
    val autoDelete: Boolean,
    val internal: Boolean
)

/** @author
  *   Esteban Gonzalez Ruales
  */

package types

import types.OpaqueTypes.ExchangeName

case class BrokerExchange(
    val exchangeName: ExchangeName,
    val exchangeType: ExchangeType,
    val durable: Boolean,
    val autoDelete: Boolean,
    val internal: Boolean,
    val queues: Set[BrokerQueue]
)

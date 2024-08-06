/** @author
  *   Esteban Gonzalez Ruales
  */

package types

import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.QueueName
import types.OpaqueTypes.RoutingKey

case class BrokerQueue(
    val queueName: QueueName,
    val exchangeName: ExchangeName,
    val durable: Boolean,
    val exclusive: Boolean,
    val autoDelete: Boolean,
    val routingKey: RoutingKey
)

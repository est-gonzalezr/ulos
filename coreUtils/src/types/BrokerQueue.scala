/** @author
  *   Esteban Gonzalez Ruales
  */

package types

import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.QueueName
import types.OpaqueTypes.RoutingKey

/** Represents a queue in the broker.
  *
  * @param queueName
  *   The name of the queue
  * @param exchangeName
  *   The name of the exchange
  * @param durable
  *   If the queue is durable
  * @param exclusive
  *   If the queue is exclusive
  * @param autoDelete
  *   If the queue is autoDelete
  * @param routingKey
  *   The routing key of the queue
  */
case class BrokerQueue(
    val queueName: QueueName,
    val exchangeName: ExchangeName,
    val durable: Boolean,
    val exclusive: Boolean,
    val autoDelete: Boolean,
    val routingKey: RoutingKey
)

/** @author
  *   Esteban Gonzalez Ruales
  */

package types

import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.QueueName
import types.OpaqueTypes.RoutingKey

/** The BrokerQueue class represents a queue in the broker.
  *
  * @param queueName
  *   the name of the queue
  * @param exchangeName
  *   the name of the exchange
  * @param durable
  *   if the queue is durable
  * @param exclusive
  *   if the queue is exclusive
  * @param autoDelete
  *   if the queue is autoDelete
  * @param routingKey
  *   the routing key of the queue
  */
case class BrokerQueue(
    val queueName: QueueName,
    val exchangeName: ExchangeName,
    val durable: Boolean,
    val exclusive: Boolean,
    val autoDelete: Boolean,
    val routingKey: RoutingKey
)

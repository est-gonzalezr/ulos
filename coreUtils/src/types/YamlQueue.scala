/** @author
  *   Esteban Gonzalez Ruales
  */

package types

import org.virtuslab.yaml.YamlCodec

/** The YamlQueue case class represents the queue configuration in the
  * queues.yaml file. It provides a way to easily decode the YAML file into a
  * Scala object.
  *
  * @param queueName
  * @param exchangeName
  * @param durable
  * @param exclusive
  * @param autoDelete
  * @param routingKey
  */
case class YamlQueue(
    val queueName: String,
    val exchangeName: String,
    val durable: Boolean,
    val exclusive: Boolean,
    val autoDelete: Boolean,
    val routingKey: String
) derives YamlCodec

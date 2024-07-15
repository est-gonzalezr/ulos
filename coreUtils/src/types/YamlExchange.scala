/** @author
  *   Esteban Gonzalez Ruales
  */

package types

import org.virtuslab.yaml.YamlCodec

/** The YamlExchange case class represents the exchange configuration in the
  * exchanges.yaml file. It provides a way to easily decode the YAML file into a
  * Scala object.
  *
  * @param exchangeName
  * @param exchangeType
  * @param durable
  * @param autoDelete
  * @param internal
  */
case class YamlExchange(
    val exchangeName: String,
    val exchangeType: String,
    val durable: Boolean,
    val autoDelete: Boolean,
    val internal: Boolean
) derives YamlCodec

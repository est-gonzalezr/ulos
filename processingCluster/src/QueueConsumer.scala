import cats.effect.IO
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import types.OpaqueTypes.RoutingKey

case class QueueConsumer(
    channel: Channel,
    successRoutingKey: RoutingKey,
    errorRoutingKey: RoutingKey,
    publishFunction: (RoutingKey, Array[Byte]) => IO[Unit]
) extends DefaultConsumer(channel):
  override def handleDelivery(
      consumerTag: String,
      envelope: Envelope,
      properties: BasicProperties,
      body: Array[Byte]
  ): Unit =
    val message = body.map(_.toChar).mkString
    println(s" [x] Received '$message' by consumer with tag $consumerTag")
    channel.basicAck(envelope.getDeliveryTag, false)
    println(s" [x] Acknowledged message with tag ${envelope.getDeliveryTag}")

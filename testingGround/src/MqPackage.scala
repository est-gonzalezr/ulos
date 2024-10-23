import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Envelope

sealed trait MqPackage:
  def packageMessageBytes: Seq[Byte]
  def ack(multiple: Boolean = false): Unit
  def reject(multiple: Boolean = false, requeue: Boolean = true): Unit
end MqPackage

final case class RabbitMqPackage(
    channel: Channel,
    envelope: Envelope,
    properties: BasicProperties,
    body: Seq[Byte]
) extends MqPackage:
  def packageMessageBytes: Seq[Byte] = body
  def ack(multiple: Boolean): Unit =
    channel.basicAck(envelope.getDeliveryTag, multiple)
  def reject(multiple: Boolean, requeue: Boolean): Unit =
    channel.basicNack(envelope.getDeliveryTag, multiple, requeue)
end RabbitMqPackage

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Consumer
import akka.actor.typed.ActorRef
import com.rabbitmq.client.DefaultConsumer
import akka.actor.typed.scaladsl.ActorContext

sealed trait MqProvider:
  def startConsumer(): Unit
end MqProvider

case object RabbitMqProvider extends MqProvider:
  def startConsumer(): Unit = ???
  def brokerConnection(
      host: String,
      port: Int,
      username: String,
      password: String
  ): Connection =
    val factory = ConnectionFactory()
    factory.setHost(host)
    factory.setPort(port)
    factory.setUsername(username)
    factory.setPassword(password)

    factory.newConnection()
  end brokerConnection

  def channelFromConnection(connection: Connection): Channel =
    connection.createChannel()
  end channelFromConnection

  def consumeMessages(
      channel: Channel,
      queueName: String,
      consumer: Consumer
  ): Unit =
    val _ = channel.basicConsume(queueName, false, consumer)
  end consumeMessages

  case class RabbitMqConsumer(
      channel: Channel,
      ref: ActorRef[MqManager.Command]
  ) extends DefaultConsumer(channel):
    override def handleDelivery(
        consumerTag: String,
        envelope: Envelope,
        properties: BasicProperties,
        body: Array[Byte]
    ): Unit =
      ref ! MqManager.DeserializeMqMessage(
        MqMessage(envelope.getDeliveryTag.toString, body.toSeq)
      )

    end handleDelivery
  end RabbitMqConsumer

end RabbitMqProvider

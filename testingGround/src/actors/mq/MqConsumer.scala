package actors.mq

/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import types.MqMessage
import types.OpaqueTypes.QueueName

// import scala.concurrent.duration.*

/** This actor conusmes from the Message Queue and sends the messages to the
  * system.
  */
object MqConsumer:
  def apply(
      replyTo: ActorRef[MqManager.Command],
      channel: Channel,
      consumptionQueue: QueueName
  ): Behavior[Nothing] =
    consuming(replyTo, channel, consumptionQueue)

  /** This behavior consumes messages from the Message Queue. Since the
    * "basicConsume" method is blocking, the behavior remains blocked until the
    * connection is closed but the actor still sends the messages to the system.
    *
    * @param replyTo
    *   The reference to the MqManager actor.
    * @param channel
    *   The channel to the Message Queue.
    *
    * @return
    *   A behavior that consumes messages from the Message Queue.
    */
  private def consuming(
      replyTo: ActorRef[MqManager.Command],
      channel: Channel,
      consumptionQueue: QueueName
  ): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      context.log.info("MqConsumer started...")

      val consumer = RabbitMqConsumer(channel, replyTo)
      val _ = channel.basicConsume(consumptionQueue.value, false, consumer)
      Behaviors.stopped
    }
  end consuming

  /** This class is a RabbitMQ consumer that sends the messages to the system.
    *
    * @param channel
    *   The channel to the Message Queue.
    * @param replyTo
    *   The reference to the MqManager actor.
    */
  private case class RabbitMqConsumer(
      channel: Channel,
      replyTo: ActorRef[MqManager.Command]
  ) extends DefaultConsumer(channel):
    override def handleDelivery(
        consumerTag: String,
        envelope: Envelope,
        properties: BasicProperties,
        body: Array[Byte]
    ): Unit =
      replyTo ! MqManager.MqProcessTask(
        MqMessage(envelope.getDeliveryTag.toString, body.toSeq)
      )
    end handleDelivery
  end RabbitMqConsumer
end MqConsumer

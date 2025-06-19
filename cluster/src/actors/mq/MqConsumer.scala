package actors.mq

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import types.MqMessage
import types.OpaqueTypes.MqQueueName

/** A persistent actor responsible for consuming messages from the message queue
  * and redirecting them to the system.
  */
object MqConsumer:
  def apply(
      channel: Channel,
      consumptionQueue: MqQueueName,
      replyTo: ActorRef[MqManager.Command]
  ): Behavior[Nothing] =
    consuming(channel, consumptionQueue, replyTo)

  /** Consumes messages from the message queue.
    *
    * @param channel
    *   The channel to the Message Queue.
    * @param consumptionQueue
    *   The queue from which the messages are consumed.
    * @param replyTo
    *   The reference to the MqManager actor.
    *
    * @return
    *   A Behavior that consumes messages from the Message Queue.
    */
  private def consuming(
      channel: Channel,
      consumptionQueue: MqQueueName,
      replyTo: ActorRef[MqManager.Command]
  ): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      context.log.info("MqConsumer started...")

      val consumer = RabbitMqConsumer(channel, replyTo)
      val _ = channel.basicConsume(consumptionQueue.value, false, consumer)
      Behaviors.stopped
    }
  end consuming

  /** A RabbitMQ consumer that consumes messages from the message queue..
    *
    * @param channel
    *   The channel to the Message Queue.
    * @param replyTo
    *   The reference to the MqManager actor.
    */
  private class RabbitMqConsumer(
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
        MqMessage(envelope.getDeliveryTag, body.toSeq)
      )
    end handleDelivery
  end RabbitMqConsumer
end MqConsumer

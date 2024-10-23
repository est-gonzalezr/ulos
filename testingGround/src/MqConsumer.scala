/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration.*
import akka.actor.typed.scaladsl.ActorContext
import com.rabbitmq.client.Channel

/** This actor conusmes from the Message Queue and sends the message to the
  * system.
  */
object MqConsumer:
  def apply(
      ref: ActorRef[MqManager.Command],
      channel: Channel
  ): Behavior[Nothing] =
    consuming(ref, channel)

  private def consuming(
      ref: ActorRef[MqManager.Command],
      channel: Channel
  ): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      context.log.info("MqConsumer started...")

      val _ = startConsumer(ref, channel)
      Behaviors.stopped
    }

  end consuming

  private def startConsumer(
      ref: ActorRef[MqManager.Command],
      channel: Channel
  ): Unit =
    val consumer = RabbitMqProvider.RabbitMqConsumer(channel, ref)

    RabbitMqProvider.consumeMessages(channel, "test", consumer)
  end startConsumer
end MqConsumer

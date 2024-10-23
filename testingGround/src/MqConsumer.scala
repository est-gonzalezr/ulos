/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration.*
import akka.actor.typed.scaladsl.ActorContext

/** This actor conusmes from the Message Queue and sends the message to the
  * system.
  */
object MqConsumer:
  // Command protocol
  sealed trait Command
  private final case class DeliverMessage(bytes: Seq[Byte]) extends Command

  def apply(ref: ActorRef[MqManager.Command]): Behavior[Nothing] =
    consuming(ref)

  private def consuming(ref: ActorRef[MqManager.Command]): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      context.log.info("MqConsumer started...")

      // temporary implementation to send messages to the system
      // Behaviors.withTimers[Nothing] { timers =>
      //   timers.startTimerWithFixedDelay(
      //     DeliverMessage("message".map(_.toByte).toSeq),
      //     3.second
      //   )

      //   Behaviors.receiveMessage[Nothing] { message =>
      //     message match
      //       case DeliverMessage(bytes) =>
      //         context.log.info(
      //           "Message received from MQ, sending to MQ Manager"
      //         )
      //         ref ! MqManager.DeserializeMqMessage(bytes)
      //         Behaviors.same
      //   }
      // }
      val _ = startConsumer(ref)
      Behaviors.stopped
    }

  end consuming

  private def startConsumer(
      ref: ActorRef[MqManager.Command]
  ): Unit =
    val connection = RabbitMqProvider.brokerConnection(
      "localhost",
      5672,
      "guest",
      "guest"
    )
    val channel = RabbitMqProvider.channelFromConnection(connection)
    val consumer = RabbitMqProvider.RabbitMqConsumer(channel, ref)

    RabbitMqProvider.consumeMessages(channel, "test", consumer)
  end startConsumer
end MqConsumer

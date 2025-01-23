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
import akka.actor.Kill

object MqConsumptionHandler:
  sealed trait Command
  final case class Reroute(mqMessage: MqMessage) extends Command
  case object Shutdown extends Command

  def apply(
      channel: Channel,
      consumptionQueue: QueueName,
      replyTo: ActorRef[MqManager.Command]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("MqConsumptionHandler started...")

      val mqConsumer = context.spawn(
        MqConsumer(channel, consumptionQueue, context.self),
        "mq-consumer"
      )

      Behaviors.receiveMessage { message =>
        message match
          case Reroute(mqMessage) =>
            context.log.info(
              s"Reroute command received. MqMessage --> $mqMessage."
            )
            replyTo ! MqManager.MqProcessTask(mqMessage)
            Behaviors.same

          case Shutdown =>
            context.log.info("Shutdown command received.")
            // mqConsumer ! Kill
            Behaviors.stopped
      }
    }
end MqConsumptionHandler

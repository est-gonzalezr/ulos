/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration.*

/** This actor conusmes from the Message Queue and sends the message to the
  * system.
  */
object MqConsumer:
  // Command protocol
  sealed trait Command
  private final case class DeliverMessage(bytes: Seq[Byte]) extends Command

  def apply(ref: ActorRef[MqManager.Command]): Behavior[Command] =
    consuming(ref)

  private def consuming(ref: ActorRef[MqManager.Command]): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.log.info("MqConsumer started...")

      // temporary implementation to send messages to the system
      Behaviors.withTimers[Command] { timers =>
        timers.startTimerWithFixedDelay(
          DeliverMessage("message".map(_.toByte).toSeq),
          3.second
        )

        Behaviors.receiveMessage[Command] { message =>
          message match
            case DeliverMessage(bytes) =>
              context.log.info(
                "Message received from MQ, sending to MQ Manager"
              )
              ref ! MqManager.DeserializeMqMessage(bytes)
              Behaviors.same
        }
      }
    }

  end consuming
end MqConsumer

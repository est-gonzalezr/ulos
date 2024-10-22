import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.{Success, Failure}

object MqConsumer:
  sealed trait Command
  case object StartConsuming extends Command

  sealed trait Response
  case object ConsumingStarted extends Response

  def apply(ref: ActorRef[MqManager.Command]): Behavior[Command] =
    consumerStart(ref)

  def consumerStart(ref: ActorRef[MqManager.Command]): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case StartConsuming =>
          context.log.info("Starting to consume messages")
          consuming(ref)
    }
  end consumerStart

  def consuming(ref: ActorRef[MqManager.Command]): Behavior[Command] =
    Behaviors.withTimers { timers =>
      timers.startTimerWithFixedDelay(StartConsuming, 3.second)

      Behaviors.receive { (context, message) =>
        ref ! MqManager.ProcessMqMessage(
          "this is a message".map(_.toByte).toSeq
        )
        Behaviors.same
      }
    }
  end consuming
end MqConsumer

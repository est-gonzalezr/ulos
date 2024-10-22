import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.{Success, Failure}

object MqAcker:
  sealed trait Command
  final case class SendAck(
      str: String,
      replyTo: ActorRef[Response]
  ) extends Command

  final case class SendNack(
      str: String,
      replyTo: ActorRef[Response]
  ) extends Command

  sealed trait Response
  case object Successful extends Response
  case object Unsuccessful extends Response

  def apply(): Behavior[Command] = processing

  def processing: Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case SendAck(str, replyTo) =>
          context.log.info(
            s"Received task of type: $str"
          )

          context.log.info(
            s"Acknowledging task: $str"
          )
          replyTo ! Successful
          Behaviors.stopped

        case SendNack(str, replyTo) =>
          context.log.info(
            s"Received task of type: $str"
          )

          context.log.info(
            s"Rejecting task: $str"
          )
          replyTo ! Unsuccessful
          Behaviors.stopped
    }
  end processing
end MqAcker

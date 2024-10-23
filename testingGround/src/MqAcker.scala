/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

object MqAcker:
  // Command protocol
  sealed trait Command
  final case class SendAck(
      str: String,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class SendNack(
      str: String,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  def apply(): Behavior[Command] = processing

  def processing: Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case SendAck(str, replyTo) =>
          context.log.info(
            s"Acknowledging task..."
          )

          sendAck(str) match
            case Right(_) =>
              context.log.info(
                s"Task acknowledged"
              )

              replyTo ! StatusReply.Ack
            case Left(error) =>
              context.log.error(
                s"Task could not be acknowledged"
              )

              replyTo ! StatusReply.Error(
                s"Task could not be acknowledged: $error"
              )
          end match
          Behaviors.stopped

        case SendNack(str, replyTo) =>
          context.log.info(
            s"Rejecting task"
          )

          sendNack(str) match
            case Right(_) =>
              context.log.info(
                s"Task rejected"
              )
              replyTo ! StatusReply.Ack
            case Left(error) =>
              context.log.error(
                s"Task $str could not be rejected"
              )
              replyTo ! StatusReply.Error(s"Task could not be rejected: $error")
          end match
          Behaviors.stopped
    }
  end processing

  def sendAck(str: String): Either[String, String] =
    Right(s"Task $str acknowledged")

  def sendNack(str: String): Either[String, String] =
    Right(s"Task $str rejected")
end MqAcker

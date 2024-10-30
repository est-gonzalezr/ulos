package actors.mq

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
import com.rabbitmq.client.Channel

import types.MqMessage

object MqCommunicator:
  // Command protocol
  sealed trait Command

  final case class SendMqMessage(
      mqMessage: MqMessage,
      ref: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class SendAck(
      mqMessage: MqMessage,
      ref: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class SendReject(
      mqMessage: MqMessage,
      ref: ActorRef[StatusReply[Done]]
  ) extends Command

  def apply(channel: Channel): Behavior[Command] = processing(channel)

  def processing(channel: Channel): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case SendMqMessage(mqMessage, ref) =>
          context.log.info(
            s"Processing message..."
          )

          sendAck(channel, mqMessage)

          ref ! StatusReply.Ack

        case SendAck(mqMessage, ref) =>
          context.log.info(
            s"Acknowledging task..."
          )

          sendAck(channel, mqMessage)

          ref ! StatusReply.Ack

        case SendReject(mqMessage, ref) =>
          context.log.info(
            s"Rejecting task"
          )

          sendReject(channel, mqMessage)

          ref ! StatusReply.Ack
      end match

      Behaviors.stopped
    }
  end processing

  def sendAck(channel: Channel, mqMessage: MqMessage): Unit =
    channel.basicAck(mqMessage.id.toLong, false)

  def sendReject(channel: Channel, mqMessage: MqMessage): Unit =
    channel.basicNack(mqMessage.id.toLong, false, true)

end MqCommunicator

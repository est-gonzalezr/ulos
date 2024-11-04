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
import types.OpaqueTypes.RoutingKey
import types.OpaqueTypes.ExchangeName

object MqCommunicator:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class SendMqMessage(
      bytes: Seq[Byte],
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      ref: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class SendAck(
      id: String,
      ref: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class SendReject(
      id: String,
      ref: ActorRef[StatusReply[Done]]
  ) extends Command

  def apply(channel: Channel): Behavior[Command] = processing(channel)

  def processing(channel: Channel): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case SendMqMessage(bytes, exchangeName, routingKey, ref) =>
          context.log.info(
            s"Sending message..."
          )

          sendmessage(
            channel,
            exchangeName,
            routingKey,
            bytes
          )

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

  def sendAck(channel: Channel, mqMessageId: String): Unit =
    mqMessageId.toLongOption match
      case Some(id) =>
        channel.basicAck(id, false)
      case None =>
        throw new Exception("Invalid message id")

  def sendReject(channel: Channel, mqMessageId: String): Unit =
    mqMessageId.toLongOption match
      case Some(id) =>
        channel.basicReject(id, false)
      case None =>
        throw new Exception("Invalid message id")

  def sendmessage(
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      message: Seq[Byte]
  ): Unit =
    channel.basicPublish(
      exchangeName.value,
      routingKey.value,
      null,
      message.toArray
    )

end MqCommunicator

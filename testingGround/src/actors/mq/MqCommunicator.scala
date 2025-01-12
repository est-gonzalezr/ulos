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
import com.rabbitmq.client.Channel
import types.MqMessage
import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.RoutingKey

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

object MqCommunicator:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class SendMqMessage(
      bytes: Seq[Byte],
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class SendAck(
      mqMessageId: String,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class SendReject(
      mqMessageId: String,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  def apply(channel: Channel): Behavior[Command] = processing(channel)

  /** This behavior processes the messages to be sent to the Message Queue.
    *
    * @param channel
    *   The channel to the Message Queue.
    *
    * @return
    *   A behavior that processes the messages to be sent to the Message Queue.
    */
  def processing(channel: Channel): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        /* SendMessage
         *
         * This command is sent by the MqManager actor to send a message to the MQ.
         */

        case SendMqMessage(bytes, exchangeName, routingKey, replyTo) =>
          context.log.info(
            s"MqCommunicator received message to send to exchange: $exchangeName and routing key: $routingKey"
          )

          sendmessage(
            channel,
            exchangeName,
            routingKey,
            bytes
          ) match
            case Success(_) =>
              context.log.info(
                s"MqCommunicator sent message to exchange: $exchangeName and routing key: $routingKey"
              )
              replyTo ! StatusReply.Ack

            case Failure(exception) =>
              context.log.error(
                s"MqCommunicator failed to send message to exchange: $exchangeName and routing key: $routingKey with error: ${exception.getMessage}"
              )
              replyTo ! StatusReply.Error(exception.getMessage)
          end match
        /* SendAck
         *
         * This command is sent by the MqManager actor to send an ack to the MQ.
         */
        case SendAck(mqMessageId, replyTo) =>
          context.log.info(
            s"MqCommunicator received ack request for mq message with id: $mqMessageId"
          )

          sendAck(channel, mqMessageId) match
            case Success(_) =>
              context.log.info(
                s"MqCommunicator sent ack for mq message with id: $mqMessageId"
              )
              replyTo ! StatusReply.Ack
            case Failure(exception) =>
              context.log.error(
                s"MqCommunicator failed to send ack for mq message with id: $mqMessageId with error: ${exception.getMessage}"
              )
              replyTo ! StatusReply.Error(exception.getMessage)
          end match
        /* SendReject
         *
         * This command is sent by the MqManager actor to send a reject to the MQ.
         */
        case SendReject(mqMessageId, replyTo) =>
          context.log.info(
            s"MqCommunicator received reject request for mq message with id: $mqMessageId"
          )

          sendReject(channel, mqMessageId) match
            case Success(_) =>
              context.log.info(
                s"MqCommunicator sent reject for mq message with id: $mqMessageId"
              )
              replyTo ! StatusReply.Ack
            case Failure(exception) =>
              context.log.error(
                s"MqCommunicator failed to send reject for mq message with id: $mqMessageId with error: ${exception.getMessage}"
              )
              replyTo ! StatusReply.Error(exception.getMessage)
          end match
      end match

      Behaviors.stopped
    }
  end processing

  /** Sends an ack to the MQ.
    *
    * @param channel
    *   The channel to the MQ.
    * @param mqMessageId
    *   The id of the message to ack.
    *
    * @return
    *   A Try[Unit] indicating the result of the operation.
    */
  def sendAck(channel: Channel, mqMessageId: String): Try[Unit] =
    Try {
      mqMessageId.toLongOption match
        case Some(id) =>
          channel.basicAck(id, false)
        case None =>
          throw new Exception("Invalid message id")
    }

  /** Sends a reject to the MQ.
    *
    * @param channel
    *   The channel to the MQ.
    * @param mqMessageId
    *   The id of the message to reject.
    *
    * @return
    *   A Try[Unit] indicating the result of the operation.
    */
  def sendReject(channel: Channel, mqMessageId: String): Try[Unit] =
    Try {
      mqMessageId.toLongOption match
        case Some(id) =>
          channel.basicReject(id, false)
        case None =>
          throw new Exception("Invalid message id")
    }

  /** Sends a message to the MQ.
    *
    * @param channel
    *   The channel to the MQ.
    * @param exchangeName
    *   The name of the exchange to send the message to.
    * @param routingKey
    *   The routing key to use to send the message.
    * @param message
    *   The message to send.
    *
    * @return
    *   A Try[Unit] indicating the result of the operation.
    */
  def sendmessage(
      channel: Channel,
      exchangeName: ExchangeName,
      routingKey: RoutingKey,
      message: Seq[Byte]
  ): Try[Unit] =
    Try {
      channel.basicPublish(
        exchangeName.value,
        routingKey.value,
        null,
        message.toArray
      )
    }

end MqCommunicator

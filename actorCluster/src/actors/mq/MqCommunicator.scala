package actors.mq

/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import com.rabbitmq.client.Channel
import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.RoutingKey

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
      mqMessageId: Long,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class SendReject(
      mqMessageId: Long,
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

        case SendMqMessage(bytes, exchangeName, routingKey, replyTo) =>
          context.log.info(
            s"SendMqMessage command received. Bytes --> ..., ExchangeName --> $exchangeName, RoutingKey --> $routingKey."
          )

          sendmessage(
            channel,
            exchangeName,
            routingKey,
            bytes
          ) match
            case Success(_) =>
              context.log.info(
                s"Send message success response received from MQ. Bytes --> ..., ExchangeName --> $exchangeName, RoutingKey --> $routingKey."
              )
              context.log.info(
                s"Sending StatusReply.Ack to MqManager. Bytes --> ..., ExchangeName --> $exchangeName, RoutingKey --> $routingKey."
              )
              replyTo ! StatusReply.Ack

            case Failure(exception) =>
              context.log.error(
                s"Send message failure response received from MQ. Bytes --> ..., ExchangeName --> $exchangeName, RoutingKey --> $routingKey. Exception thrown: ${exception.getMessage}."
              )
              context.log.info(
                s"Sending StatusReply.Error to MqManager. Bytes --> ..., ExchangeName --> $exchangeName, RoutingKey --> $routingKey."
              )
              replyTo ! StatusReply.Error(exception)
          end match

        case SendAck(mqMessageId, replyTo) =>
          context.log.info(s"SendAck command received. MqId: $mqMessageId.")

          sendAck(channel, mqMessageId) match
            case Success(_) =>
              context.log.info(
                s"Ack success reponse received from MQ. MqId: $mqMessageId."
              )
              context.log.info(
                s"Sending StatusReply.Ack to MqManager. MqId: $mqMessageId."
              )
              replyTo ! StatusReply.Ack
            case Failure(exception) =>
              context.log.error(
                s"Ack failure response received from MQ. MqId: $mqMessageId. Exception thrown: ${exception.getMessage}."
              )
              context.log.info(
                s"Sending StatusReply.Error to MqManager. MqId: $mqMessageId."
              )
              replyTo ! StatusReply.Error(exception)
          end match

        case SendReject(mqMessageId, replyTo) =>
          context.log.info(s"SendReject command received. MqId: $mqMessageId.")

          sendReject(channel, mqMessageId) match
            case Success(_) =>
              context.log.info(
                s"Reject success response received from MQ. MqId: $mqMessageId."
              )
              context.log.info(
                s"Sending StatusReply.Ack to MqManager. MqId: $mqMessageId."
              )
              replyTo ! StatusReply.Ack
            case Failure(exception) =>
              context.log.error(
                s"Reject failure response received from MQ. MqId: $mqMessageId. Exception thrown: ${exception.getMessage}."
              )
              context.log.info(
                s"Sending StatusReply.Error to MqManager. MqId: $mqMessageId."
              )
              replyTo ! StatusReply.Error(exception)
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
  def sendAck(channel: Channel, mqMessageId: Long): Try[Unit] =
    Try(channel.basicAck(mqMessageId, false))

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
  def sendReject(channel: Channel, mqMessageId: Long): Try[Unit] =
    Try(channel.basicReject(mqMessageId, false))

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

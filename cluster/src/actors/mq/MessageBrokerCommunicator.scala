package actors.mq

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.rabbitmq.client.Channel
import types.OpaqueTypes.MessageBrokerExchangeName
import types.OpaqueTypes.MessageBrokerRoutingKey
import types.Task

/** A stateless actor responsible for communicating outbound messages to the
  * message queue.
  */
object MessageBrokerCommunicator:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class PublishTask(
      task: Task,
      bytes: Seq[Byte],
      exchangeName: MessageBrokerExchangeName,
      routingKey: MessageBrokerRoutingKey
  ) extends Command
  final case class AckMessage(mqMessageId: Long) extends Command
  final case class RejectMessage(mqMessageId: Long) extends Command

  // Response protocol
  sealed trait Response

  final case class TaskPublished(task: Task) extends Response
  final case class MessageAcknowledged(mqMessageId: Long) extends Response
  final case class MessageRejected(mqMessageId: Long) extends Response

  def apply(channel: Channel, replyTo: ActorRef[Response]): Behavior[Command] =
    handleMessages(channel, replyTo)

  /** Handles outgoing messages for the message queue.
    *
    * @param channel
    *   The channel used to publish messages.
    *
    * @return
    *   A Behavior that tries to send the desired action to the message queue.
    */
  private def handleMessages(
      channel: Channel,
      replyTo: ActorRef[Response]
  ): Behavior[Command] =
    Behaviors.receive { (_, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case PublishTask(task, bytes, exchangeName, routingKey) =>
          publishMessage(
            channel,
            exchangeName,
            routingKey,
            bytes
          )

          replyTo ! TaskPublished(task)

        case AckMessage(mqMessageId) =>
          ackMessage(channel, mqMessageId)
          replyTo ! MessageAcknowledged(mqMessageId)

        case RejectMessage(mqMessageId) =>
          rejectMessage(channel, mqMessageId)
          replyTo ! MessageRejected(mqMessageId)

      end match

      Behaviors.stopped
    }
  end handleMessages

  /** Sends an ack to the MQ.
    *
    * @param channel
    *   The channel to the MQ.
    * @param mqMessageId
    *   The id of the message to ack.
    *
    * @return
    *   Unit
    */
  private def ackMessage(channel: Channel, mqMessageId: Long): Unit =
    channel.basicAck(mqMessageId, false)

  /** Sends a reject to the MQ.
    *
    * @param channel
    *   The channel to the MQ.
    * @param mqMessageId
    *   The id of the message to reject.
    *
    * @return
    *   Unit
    */
  private def rejectMessage(channel: Channel, mqMessageId: Long): Unit =
    channel.basicReject(mqMessageId, false)

  /** Publishes a message to the MQ.
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
    *   Unit
    */
  private def publishMessage(
      channel: Channel,
      exchangeName: MessageBrokerExchangeName,
      routingKey: MessageBrokerRoutingKey,
      message: Seq[Byte]
  ): Unit =
    channel.basicPublish(
      exchangeName.value,
      routingKey.value,
      null,
      message.toArray
    )
end MessageBrokerCommunicator

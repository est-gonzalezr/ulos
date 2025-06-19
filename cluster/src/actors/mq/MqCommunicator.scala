package actors.mq

import scala.util.Try

import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import com.rabbitmq.client.Channel
import types.OpaqueTypes.MqExchangeName
import types.OpaqueTypes.RoutingKey

/** A stateless actor responsible for communicating outbound messages to the
  * message queue.
  */
object MqCommunicator:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class PublishMessage(
      bytes: Seq[Byte],
      exchangeName: MqExchangeName,
      routingKey: RoutingKey,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class AckMessage(
      mqMessageId: Long,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class RejectMessage(
      mqMessageId: Long,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command
  final case class SetPrefetchCount(
      prefetchCount: Int,
      replyTo: ActorRef[StatusReply[Done]]
  ) extends Command

  def apply(channel: Channel): Behavior[Command] = handleMessages(channel)

  /** Handles outgoing messages for the message queue.
    *
    * @param channel
    *   The channel used to publish messages.
    *
    * @return
    *   A Behavior that tries to send the desired action to the message queue.
    */
  private def handleMessages(channel: Channel): Behavior[Command] =
    Behaviors.receive { (_, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case PublishMessage(bytes, exchangeName, routingKey, replyTo) =>
          replyTo ! toStatusReply(
            publishMessage(
              channel,
              exchangeName,
              routingKey,
              bytes
            )
          )

        case AckMessage(mqMessageId, replyTo) =>
          replyTo ! toStatusReply(
            ackMessage(channel, mqMessageId)
          )

        case RejectMessage(mqMessageId, replyTo) =>
          replyTo ! toStatusReply(
            rejectMessage(channel, mqMessageId)
          )

        case SetPrefetchCount(prefetchCount, replyTo) =>
          replyTo ! toStatusReply(
            setPrefetchCount(channel, prefetchCount)
          )

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
    *   A Try indicating the result of the operation.
    */
  private def ackMessage(channel: Channel, mqMessageId: Long): Try[Unit] =
    Try(channel.basicAck(mqMessageId, false))

  /** Sends a reject to the MQ.
    *
    * @param channel
    *   The channel to the MQ.
    * @param mqMessageId
    *   The id of the message to reject.
    *
    * @return
    *   A Try indicating the result of the operation.
    */
  private def rejectMessage(channel: Channel, mqMessageId: Long): Try[Unit] =
    Try(channel.basicReject(mqMessageId, false))

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
    *   A Try indicating the result of the operation.
    */
  private def publishMessage(
      channel: Channel,
      exchangeName: MqExchangeName,
      routingKey: RoutingKey,
      message: Seq[Byte]
  ): Try[Unit] =
    Try(
      channel.basicPublish(
        exchangeName.value,
        routingKey.value,
        null,
        message.toArray
      )
    )

  /** Sets the prefetch count for the channel.
    *
    * @param channel
    *   The channel to the MQ.
    * @param count
    *   The prefetch count to set.
    *
    * @return
    *   A Try indicating the result of the operation.
    */
  private def setPrefetchCount(channel: Channel, count: Int): Try[Unit] =
    Try(channel.basicQos(count, false))

    /** Covnverts the result of a Try action into a StatusReply.
      *
      * @param actionResult
      *   The result of the action.
      *
      * @return
      *   A StatusReply indicating the result of the operation.
      */
  private def toStatusReply[T](
      actionResult: Try[T]
  ): StatusReply[Done] =
    actionResult.fold(
      StatusReply.Error(_),
      _ => StatusReply.Ack
    )

end MqCommunicator

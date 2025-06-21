package actors.mq

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.rabbitmq.client.Channel
import types.OpaqueTypes.MessageBrokerExchangeName
import types.OpaqueTypes.MessageBrokerRoutingKey
import types.Task

/** A stateless actor responsible for communicating outbound messages to the
  * message broker.
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
  final case class AckTask(task: Task) extends Command
  final case class RejectTask(task: Task) extends Command

  // Response protocol
  sealed trait Response

  final case class TaskPublished(task: Task) extends Response
  final case class TaskAcknowledged(task: Task) extends Response
  final case class TaskRejected(task: Task) extends Response

  def apply(channel: Channel, replyTo: ActorRef[Response]): Behavior[Command] =
    handleMessages(channel, replyTo)

  /** Handles outgoing messages for the message broker.
    *
    * @param channel
    *   The channel used to publish messages.
    *
    * @return
    *   A Behavior that tries to send the desired action to the message broker.
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
          channel.basicPublish(
            exchangeName.value,
            routingKey.value,
            null,
            bytes.toArray
          )

          replyTo ! TaskPublished(task)

        case AckTask(task) =>
          channel.basicAck(task.mqId, false)
          replyTo ! TaskAcknowledged(task)

        case RejectTask(task) =>
          channel.basicReject(task.mqId, true)
          replyTo ! TaskRejected(task)

      end match

      Behaviors.stopped
    }
  end handleMessages
end MessageBrokerCommunicator

package actors.mq

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Connection
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import types.OpaqueTypes.MessageBrokerExchange
import types.OpaqueTypes.MessageBrokerRoutingKey
import types.PublishTarget
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
      exchangeName: MessageBrokerExchange,
      routingKey: MessageBrokerRoutingKey,
      publishTarget: PublishTarget
  ) extends Command
  final case class AckTask(task: Task) extends Command
  final case class RejectTask(task: Task) extends Command

  // Response protocol
  sealed trait Response

  final case class TaskPublished(task: Task, publishTarget: PublishTarget)
      extends Response
  final case class TaskAcknowledged(task: Task) extends Response
  final case class TaskRejected(task: Task) extends Response

  def apply(
      connection: Connection,
      replyTo: ActorRef[Response]
  ): Behavior[Command] =
    handleMessages(connection, replyTo)

  /** Handles outgoing messages for the message broker.
    *
    * @param connection
    *   The connection used to communicate with the message broker.
    * @param replyTo
    *   The actor that will receive the response from the message broker.
    *
    * @return
    *   A Behavior that tries to send the desired action to the message broker.
    */
  private def handleMessages(
      connection: Connection,
      replyTo: ActorRef[Response]
  ): Behavior[Command] =
    Behaviors.receive { (_, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case PublishTask(
              task,
              bytes,
              exchangeName,
              routingKey,
              publishTarget
            ) =>
          val channel = connection.createChannel()

          channel.basicPublish(
            exchangeName.value,
            routingKey.value,
            BasicProperties.Builder().deliveryMode(2).build(),
            bytes.toArray
          )

          channel.close()

          replyTo ! TaskPublished(task, publishTarget)

        case AckTask(task) =>
          val channel = connection.createChannel()

          channel.basicAck(task.mqId, false)
          replyTo ! TaskAcknowledged(task)

          channel.close()

        case RejectTask(task) =>
          val channel = connection.createChannel()

          channel.basicReject(task.mqId, false)
          replyTo ! TaskRejected(task)

          channel.close()

      end match

      Behaviors.stopped
    }
  end handleMessages
end MessageBrokerCommunicator

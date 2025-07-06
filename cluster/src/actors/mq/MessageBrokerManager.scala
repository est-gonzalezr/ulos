package actors.mq

import actors.Orchestrator
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.ChildFailed
import org.apache.pekko.actor.typed.PostStop
import org.apache.pekko.actor.typed.PreRestart
import org.apache.pekko.actor.typed.SupervisorStrategy
import org.apache.pekko.actor.typed.Terminated
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.util.Timeout
import types.MessageBrokerConfigurations
import types.MessageBrokerRoutingInfo
import types.MqMessage
import types.OpaqueTypes.MessageBrokerExchange
import types.OpaqueTypes.MessageBrokerQueue
import types.OpaqueTypes.MessageBrokerRoutingKey
import types.PublishTarget
import types.Task

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

val MaxConsecutiveRestarts = 5

/** A persistent actor responsible for managing actors with message queue
  * related tasks. It acts as the intermediary between the message queue and the
  * system that processes the tasks.
  */
object MessageBrokerManager:
  given Timeout = 10.seconds

  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ProcessTask(mqMessage: MqMessage) extends Command
  final case class AckTask(task: Task) extends Command
  final case class RejectTask(task: Task) extends Command
  final case class PublishTask(
      task: Task,
      routingInfo: MessageBrokerRoutingInfo,
      target: PublishTarget
  ) extends Command

  // Private command protocol
  private final case class PublishSerializedTask(
      task: Task,
      bytes: Seq[Byte],
      exchange: MessageBrokerExchange,
      routingKey: MessageBrokerRoutingKey,
      publishTarget: PublishTarget
  ) extends Command
  private final case class DeliverToOrchestrator(task: Task) extends Command
  private final case class ChannelDropped(th: Throwable) extends Command
  private case object NoOp extends Command

  // Response protocol
  sealed trait Response
  sealed trait FailureResponse extends Response

  final case class TaskPublished(task: Task, publishTarget: PublishTarget)
      extends Response
  final case class TaskAcknowledged(task: Task) extends Response
  final case class TaskRejected(task: Task) extends Response

  private type CommandOrResponse = Command | MessageBrokerCommunicator.Response

  def apply(
      connParams: MessageBrokerConfigurations,
      consumptionQueue: MessageBrokerQueue,
      replyTo: ActorRef[Orchestrator.Command | Response]
  ): Behavior[CommandOrResponse] =
    setup(
      connParams,
      consumptionQueue,
      replyTo
    )
  end apply

  /** Sets up the actor.
    *
    * @param connParams
    *   Parameters for connecting to the message broker.
    * @param consumptionQueue
    *   Name of the queue to consume messages from.
    * @param replyTo
    *   Reference to reply to with messages.
    *
    * @return
    *   A Behavior that processes the messages sent to the actor.
    */
  private def setup(
      connParams: MessageBrokerConfigurations,
      consumptionQueue: MessageBrokerQueue,
      replyTo: ActorRef[Orchestrator.Command | Response]
  ): Behavior[CommandOrResponse] =
    Behaviors.setup[CommandOrResponse] { context =>
      context.log.info("MessageBrokerManager started...")

      val connection = initializeBrokerLink(connParams)
      val channel = connection.createChannel()

      channel.confirmSelect()
      channel.basicQos(
        if connParams.prefetchCount >= 0 then connParams.prefetchCount else 0,
        false
      )

      channel.addShutdownListener(reason =>
        context.self ! ChannelDropped(reason)
      )

      val consumer = RabbitMqConsumer(channel, context.self)

      val supervisedCommunicator =
        Behaviors
          .supervise(
            MessageBrokerCommunicator(
              channel,
              connParams.requeueOnReject,
              context.self
            )
          )
          .onFailure(
            SupervisorStrategy.restart
              .withLimit(MaxConsecutiveRestarts, 10.seconds)
          )
      val communicator = context.spawnAnonymous(supervisedCommunicator)
      context.watch(communicator)

      val _ =
        channel.basicConsume(consumptionQueue.value, false, consumer)
      handleMessages(connection, channel, communicator, replyTo)
    }
  end setup

  /** Handles messages received by the actor.
    *
    * @param connection
    *   The connection to the message broker.
    * @param channel
    *   The channel to the message broker.
    * @param communicator
    *   The actor responsible for communicating with the message broker.
    * @param replyTo
    *   Reference to reply to with messages.
    *
    * @return
    *   A Behavior that handles messages received by the actor.
    */
  private def handleMessages(
      connection: Connection,
      channel: Channel,
      communicator: ActorRef[MessageBrokerCommunicator.Command],
      replyTo: ActorRef[Orchestrator.Command | Response]
  ): Behavior[CommandOrResponse] =
    Behaviors
      .receive[CommandOrResponse] { (context, message) =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          case ProcessTask(mqMessage) =>
            val deserializer = context.spawnAnonymous(MessageBrokerTranslator())

            context
              .askWithStatus[MessageBrokerTranslator.DeserializeMessage, Task](
                deserializer,
                replyTo =>
                  MessageBrokerTranslator
                    .DeserializeMessage(mqMessage.body, replyTo)
              ) {
                case Success(task) =>
                  DeliverToOrchestrator(
                    task.copy(mqId = mqMessage.envelope.getDeliveryTag())
                  )

                case Failure(_) =>
                  RejectTask(
                    Task(
                      taskId = "",
                      taskOwnerId = "",
                      filePath = os.Path("/"),
                      timeout = 0.seconds,
                      routingTree = None,
                      logMessage = Some("Failed to deserialize message"),
                      mqId = mqMessage.envelope.getDeliveryTag()
                    )
                  )
              }

          case AckTask(task) =>
            communicator ! MessageBrokerCommunicator.AckTask(task)

          case RejectTask(task) =>
            communicator ! MessageBrokerCommunicator.RejectTask(task)

          case PublishTask(task, routingInfo, publishTarget) =>
            val serializer = context.spawnAnonymous(MessageBrokerTranslator())

            context.askWithStatus[MessageBrokerTranslator.SerializeMessage, Seq[
              Byte
            ]](
              serializer,
              replyTo => MessageBrokerTranslator.SerializeMessage(task, replyTo)
            ) {
              case Success(bytes) =>
                PublishSerializedTask(
                  task,
                  bytes,
                  routingInfo.exchange,
                  routingInfo.routingKey,
                  publishTarget
                )
              case Failure(_) => // this case will never happen
                NoOp
            }

          /* **********************************************************************
           * Private commands
           * ********************************************************************** */

          case PublishSerializedTask(
                task,
                bytes,
                exchange,
                routingKey,
                publishTarget
              ) =>

            communicator ! MessageBrokerCommunicator.PublishTask(
              task,
              bytes,
              exchange,
              routingKey,
              publishTarget
            )

          case DeliverToOrchestrator(task) =>
            replyTo ! Orchestrator.ProcessTask(task)

          case ChannelDropped(th) =>
            context.log.error(s"Connection dropped with reason - $th")
            throw th

          case NoOp =>

          /* **********************************************************************
           * Responses
           * ********************************************************************** */

          case MessageBrokerCommunicator.TaskAcknowledged(task) =>
            replyTo ! TaskAcknowledged(task)

          case MessageBrokerCommunicator.TaskRejected(task) =>
            replyTo ! TaskRejected(task)

          case MessageBrokerCommunicator.TaskPublished(task, publishTarget) =>
            replyTo ! TaskPublished(task, publishTarget)

        end match
        Behaviors.same
      }
      .receiveSignal {
        case (context, ChildFailed(ref, reason)) =>
          context.log.error(s"Reference $ref failed with reason - $reason")
          Behaviors.same

        case (context, Terminated(ref)) =>
          context.log.info(s"Reference $ref terminated")
          Behaviors.same

        case (_, PreRestart) =>
          if channel.isOpen() then channel.close()
          end if
          if connection.isOpen() then connection.close()
          end if
          Behaviors.same

        case (_, PostStop) =>
          if channel.isOpen() then channel.close()
          end if
          if connection.isOpen() then connection.close()
          end if
          Behaviors.same
      }
  end handleMessages

  /** Initializes the broker link.
    *
    * @param connParams
    *   The connection parameters.
    *
    * @return
    *   A Try containing the initialized connection and channel.
    */
  private def initializeBrokerLink(
      connParams: MessageBrokerConfigurations
  ): Connection =
    val factory = ConnectionFactory()
    factory.setAutomaticRecoveryEnabled(false)
    factory.setHost(connParams.host.value)
    factory.setPort(connParams.port.value)
    factory.setUsername(connParams.username.value)
    factory.setPassword(connParams.password.value)
    factory.newConnection()
  end initializeBrokerLink

  /** A RabbitMQ consumer that consumes messages from the message queue..
    *
    * @param channel
    *   The channel to the Message Queue.
    * @param replyTo
    *   The reference to the MqManager actor.
    */
  private class RabbitMqConsumer(
      channel: Channel,
      replyTo: ActorRef[MessageBrokerManager.Command]
  ) extends DefaultConsumer(channel):
    override def handleDelivery(
        consumerTag: String,
        envelope: Envelope,
        properties: BasicProperties,
        body: Array[Byte]
    ): Unit =
      replyTo ! MessageBrokerManager.ProcessTask(
        MqMessage(consumerTag, envelope, properties, body.toSeq)
      )
    end handleDelivery
  end RabbitMqConsumer
end MessageBrokerManager

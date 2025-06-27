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
import types.MessageBrokerConnectionParams
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
  private case object NoOp extends Command
  private final case class ChildCrashed(
      ref: ActorRef[Nothing],
      reason: Throwable
  ) extends Command
  private final case class ChildTerminated(ref: ActorRef[Nothing])
      extends Command

  // Response protocol
  sealed trait Response
  sealed trait FailureResponse extends Response

  final case class TaskPublished(task: Task, publishTarget: PublishTarget)
      extends Response
  final case class TaskAcknowledged(task: Task) extends Response
  final case class TaskRejected(task: Task) extends Response
  final case class TaskPublishFailed(task: Task, reason: Throwable)
      extends FailureResponse
  final case class TaskAckFailed(task: Task, reason: Throwable)
      extends FailureResponse
  final case class TaskRejectFailed(task: Task, reason: Throwable)
      extends FailureResponse

  private type CommandOrResponse = Command | MessageBrokerCommunicator.Response

  def apply(
      connParams: MessageBrokerConnectionParams,
      consumptionQueue: MessageBrokerQueue,
      prefetchCount: Int,
      replyTo: ActorRef[Orchestrator.Command | Response]
  ): Behavior[CommandOrResponse] =
    setup(
      connParams,
      consumptionQueue,
      prefetchCount,
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
      connParams: MessageBrokerConnectionParams,
      consumptionQueue: MessageBrokerQueue,
      prefetchCount: Int,
      replyTo: ActorRef[Orchestrator.Command | Response]
  ): Behavior[CommandOrResponse] =
    Behaviors.setup[CommandOrResponse] { context =>
      context.log.info("MessageBrokerManager started...")

      val (connection, channel) = initializeBrokerLink(connParams)
      val consumer = RabbitMqConsumer(channel, context.self)

      channel.confirmSelect()

      // hardcoded value for prefetch count, might change in the future
      channel.basicQos(if prefetchCount >= 0 then prefetchCount else 0, false)

      val _ = channel.basicConsume(consumptionQueue.value, false, consumer)
      handleMessages(connection, channel, replyTo)
    }
  end setup

  /** Handles messages received by the actor.
    *
    * @param connection
    *   The connection to the message broker.
    * @param channel
    *   The channel to the message broker.
    * @param replyTo
    *   Reference to reply to with messages.
    * @param failureResponse
    *   Map of child references to failure response functions in case of a child
    *   failure.
    *
    * @return
    *   A Behavior that handles messages received by the actor.
    */
  private def handleMessages(
      connection: Connection,
      channel: Channel,
      replyTo: ActorRef[Orchestrator.Command | Response],
      failureResponse: Map[ActorRef[?], Throwable => FailureResponse] = Map()
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
            Behaviors.same

          case AckTask(task) =>
            val supervisedWorker =
              Behaviors
                .supervise(MessageBrokerCommunicator(channel, context.self))
                .onFailure(SupervisorStrategy.stop)
            val worker = context.spawnAnonymous(supervisedWorker)
            context.watch(worker)

            worker ! MessageBrokerCommunicator.AckTask(task)

            handleMessages(
              connection,
              channel,
              replyTo,
              failureResponse + (worker -> (th => TaskAckFailed(task, th)))
            )

          case RejectTask(task) =>
            val supervisedWorker =
              Behaviors
                .supervise(MessageBrokerCommunicator(channel, context.self))
                .onFailure(SupervisorStrategy.stop)
            val worker = context.spawnAnonymous(supervisedWorker)
            context.watch(worker)

            worker ! MessageBrokerCommunicator.RejectTask(task)

            handleMessages(
              connection,
              channel,
              replyTo,
              failureResponse + (worker -> (th => TaskRejectFailed(task, th)))
            )

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

            Behaviors.same

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
            val supervisedWorker =
              Behaviors
                .supervise(MessageBrokerCommunicator(channel, context.self))
                .onFailure(SupervisorStrategy.stop)
            val worker =
              context.spawnAnonymous(supervisedWorker)
            context.watch(worker)

            worker ! MessageBrokerCommunicator.PublishTask(
              task,
              bytes,
              exchange,
              routingKey,
              publishTarget
            )

            handleMessages(
              connection,
              channel,
              replyTo,
              failureResponse + (worker -> (th => TaskPublishFailed(task, th)))
            )

          case DeliverToOrchestrator(task) =>
            replyTo ! Orchestrator.ProcessTask(task)
            Behaviors.same

          case NoOp =>
            Behaviors.same

          case ChildCrashed(ref, reason) =>
            failureResponse.get(ref) match
              case Some(errorApplicationFunction) =>
                replyTo ! errorApplicationFunction(reason)
                handleMessages(
                  connection,
                  channel,
                  replyTo,
                  failureResponse - ref
                )
              case None =>
                context.log.error(
                  s"Reference $ref not found, crash reason - $reason"
                )
                Behaviors.same
            end match

          case ChildTerminated(ref) =>
            if failureResponse.contains(ref) then
              handleMessages(
                connection,
                channel,
                replyTo,
                failureResponse - ref
              )
            else
              context.log.error(s"Reference $ref not found")
              Behaviors.same
            end if

          /* **********************************************************************
           * Responses
           * ********************************************************************** */

          case MessageBrokerCommunicator.TaskAcknowledged(task) =>
            replyTo ! TaskAcknowledged(task)
            Behaviors.same

          case MessageBrokerCommunicator.TaskRejected(task) =>
            replyTo ! TaskRejected(task)
            Behaviors.same

          case MessageBrokerCommunicator.TaskPublished(task, publishTarget) =>
            replyTo ! TaskPublished(task, publishTarget)
            Behaviors.same

        end match
      }
      .receiveSignal {
        case (context, ChildFailed(ref, reason)) =>
          context.self ! ChildCrashed(ref, reason)
          Behaviors.same

        case (context, Terminated(ref)) =>
          context.self ! ChildTerminated(ref)
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
      connParams: MessageBrokerConnectionParams
  ): (Connection, Channel) =
    val factory = ConnectionFactory()
    factory.setHost(connParams.host.value)
    factory.setPort(connParams.port.value)
    factory.setUsername(connParams.username.value)
    factory.setPassword(connParams.password.value)
    val connection = factory.newConnection()
    val channel = connection.createChannel()
    (connection, channel)
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

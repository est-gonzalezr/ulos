package actors.mq

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import actors.Orchestrator
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.ChildFailed
import akka.actor.typed.PostStop
import akka.actor.typed.PreRestart
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.Terminated
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import types.MessageQueueConnectionParams
import types.MqMessage
import types.OpaqueTypes.MessageBrokerExchangeName
import types.OpaqueTypes.MessageBrokerQueueName
import types.OpaqueTypes.MessageBrokerRoutingKey
import types.Task

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
      exchange: MessageBrokerExchangeName,
      routingKey: MessageBrokerRoutingKey
  ) extends Command

  // Private command protocol
  private final case class PublishSerializedTask(
      task: Task,
      bytes: Seq[Byte],
      exchange: MessageBrokerExchangeName,
      routingKey: MessageBrokerRoutingKey
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

  final case class TaskPublished(task: Task) extends Response
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
      connParams: MessageQueueConnectionParams,
      consumptionQueue: MessageBrokerQueueName,
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
      connParams: MessageQueueConnectionParams,
      consumptionQueue: MessageBrokerQueueName,
      replyTo: ActorRef[Orchestrator.Command | Response]
  ): Behavior[CommandOrResponse] =
    Behaviors
      .setup[CommandOrResponse] { context =>
        context.log.info("MqManager started...")

        val behavior = initializeBrokerLink(
          connParams
        ).fold(
          th =>
            context.log.error(
              s"Connection to broker failed. Host --> ${connParams.host.value}, Port --> ${connParams.port.value}. th: ${th.getMessage}"
            )
            Behaviors.stopped
          ,
          (connection, channel) =>
            val consumer = RabbitMqConsumer(channel, context.self)
            val _ =
              channel.basicConsume(consumptionQueue.value, false, consumer)
            handleMessages(connection, channel, replyTo)
        )

        behavior
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
      failureResponse: Map[ActorRef[Nothing], Throwable => FailureResponse] =
        Map()
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
                  MessageBrokerTranslator.DeserializeMessage(mqMessage, replyTo)
              ) {
                case Success(task) =>
                  DeliverToOrchestrator(task)

                case Failure(_) =>
                  RejectTask(
                    Task(
                      taskId = "",
                      taskOwnerId = "",
                      filePath = os.Path("/"),
                      timeout = 0.seconds,
                      routingKeys = List.empty[String],
                      logMessage = Some("Failed to deserialize message"),
                      mqId = mqMessage.mqId
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

          case PublishTask(task, exchange, routingKey) =>
            val serializer = context.spawnAnonymous(MessageBrokerTranslator())

            context.askWithStatus[MessageBrokerTranslator.SerializeMessage, Seq[
              Byte
            ]](
              serializer,
              replyTo => MessageBrokerTranslator.SerializeMessage(task, replyTo)
            ) {
              case Success(bytes) =>
                PublishSerializedTask(task, bytes, exchange, routingKey)
              case Failure(_) => // this case will never happen
                NoOp
            }

            Behaviors.same

          /* **********************************************************************
           * Private commands
           * ********************************************************************** */

          case PublishSerializedTask(task, bytes, exchange, routingKey) =>
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
              routingKey
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
                context.log.error(s"Reference $ref not found.")
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
              context.log.error(s"Reference $ref not found.")
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

          case MessageBrokerCommunicator.TaskPublished(task) =>
            replyTo ! TaskPublished(task)
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

        case (context, PreRestart) =>
          context.log.info("PreRestart for MessageBrokerManager")
          connection.close()
          channel.close()
          Behaviors.same

        case (context, PostStop) =>
          context.log.info("PostStop for MessageBrokerManager")
          connection.close()
          channel.close()
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
      connParams: MessageQueueConnectionParams
  ): Try[(Connection, Channel)] =
    Try {
      val factory = ConnectionFactory()
      factory.setHost(connParams.host.value)
      factory.setPort(connParams.port.value)
      factory.setUsername(connParams.username.value)
      factory.setPassword(connParams.password.value)
      factory.newConnection()
    }.map { connection =>
      val channel = connection.createChannel()
      (connection, channel)
    }
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
        MqMessage(envelope.getDeliveryTag, body.toSeq)
      )
    end handleDelivery
  end RabbitMqConsumer
end MessageBrokerManager

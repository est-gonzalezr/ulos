package actors.mq

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import actors.Orchestrator
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.ChildFailed
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
import types.OpaqueTypes.MqExchangeName
import types.OpaqueTypes.MqQueueName
import types.OpaqueTypes.RoutingKey
import types.Task

/** A persistent actor responsible for managing actors with message queue
  * related tasks. It acts as the intermediary between the message queue and the
  * system that processes the tasks.
  */
object MqManager:
  given Timeout = 10.seconds

  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class ProcessTask(mqMessage: MqMessage) extends Command
  final case class AckTask(mqMessageId: Long) extends Command
  final case class RejectTask(mqMessageId: Long) extends Command
  final case class PublishTask(
      task: Task,
      exchange: MqExchangeName,
      routingKey: RoutingKey
  ) extends Command

  // Private command protocol
  private final case class PublishSerializedTask(
      task: Task,
      bytes: Seq[Byte],
      exchange: MqExchangeName,
      routingKey: RoutingKey
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

  final case class MessagePublished(task: Task) extends Response
  final case class MessagePublishFailed(task: Task, reason: Throwable)
      extends FailureResponse

  private enum FailType: // esto lo tengo que hacer hoy
    case AckFailure
    case RejectFailure
    case PublishFailure
  end FailType

  private type CommandOrResponse = Command | MqCommunicator.Response

  def apply(
      connParams: MessageQueueConnectionParams,
      consumptionQueue: MqQueueName,
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
    *   parameters for connecting to the message queue
    * @param consumptionQueue
    *   name of the queue to consume messages from
    * @param replyTo
    *   reference to the Orchestrator actor
    *
    * @return
    *   a Behavior that processes the messages sent to the actor
    */
  private def setup(
      connParams: MessageQueueConnectionParams,
      consumptionQueue: MqQueueName,
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
    * @param setup
    *   The setup information for the MqManager actor.
    *
    * @return
    *   A Behavior that handles messages received by the actor.
    */
  private def handleMessages(
      connection: Connection,
      channel: Channel,
      replyTo: ActorRef[Orchestrator.Command | Response],
      failureResponse: Map[ActorRef[Nothing], Task] = Map()
  ): Behavior[CommandOrResponse] =
    Behaviors
      .receive[CommandOrResponse] { (context, message) =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          case ProcessTask(mqMessage) =>
            val deserializer = context.spawnAnonymous(MqTranslator())

            context.askWithStatus[MqTranslator.DeserializeMessage, Task](
              deserializer,
              replyTo => MqTranslator.DeserializeMessage(mqMessage, replyTo)
            ) {
              case Success(task) =>
                DeliverToOrchestrator(task)

              case Failure(_) =>
                RejectTask(mqMessage.mqId)
            }
            Behaviors.same

          case AckTask(mqMessageId) =>
            val supervisedWorker =
              Behaviors
                .supervise(MqCommunicator(channel, context.self))
                .onFailure(SupervisorStrategy.stop)
            val communicator = context.spawnAnonymous(supervisedWorker)

            communicator ! MqCommunicator.AckMessage(mqMessageId)

            Behaviors.same

          case RejectTask(mqMessageId) =>
            val supervisedWorker =
              Behaviors
                .supervise(MqCommunicator(channel, context.self))
                .onFailure(SupervisorStrategy.stop)
            val communicator = context.spawnAnonymous(supervisedWorker)

            communicator ! MqCommunicator.RejectMessage(mqMessageId)

            Behaviors.same

          case PublishTask(task, exchange, routingKey) =>
            val serializer = context.spawnAnonymous(MqTranslator())

            context.askWithStatus[MqTranslator.SerializeMessage, Seq[Byte]](
              serializer,
              replyTo => MqTranslator.SerializeMessage(task, replyTo)
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
                .supervise(MqCommunicator(channel, context.self))
                .onFailure(SupervisorStrategy.stop)
            val worker =
              context.spawnAnonymous(supervisedWorker)
            context.watch(worker)

            worker ! MqCommunicator.PublishTask(
              task,
              bytes,
              exchange,
              routingKey
            )

            handleMessages(
              connection,
              channel,
              replyTo,
              failureResponse + (worker -> task)
            )

          case DeliverToOrchestrator(task) =>
            replyTo ! Orchestrator.ProcessTask(task)
            Behaviors.same

          case NoOp =>
            Behaviors.same

          case ChildCrashed(ref, reason) =>
            failureResponse.get(ref) match
              case Some(task) =>
                replyTo ! MessagePublishFailed(task, reason)
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

          case MqCommunicator.MessageAcknowledged(_) =>
            Behaviors.same

          case MqCommunicator.MessageRejected(_) =>
            Behaviors.same

          case MqCommunicator.MessagePublished(task) =>
            replyTo ! MessagePublished(task)
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
          println("PreRestart")
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
      replyTo: ActorRef[MqManager.Command]
  ) extends DefaultConsumer(channel):
    override def handleDelivery(
        consumerTag: String,
        envelope: Envelope,
        properties: BasicProperties,
        body: Array[Byte]
    ): Unit =
      replyTo ! MqManager.ProcessTask(
        MqMessage(envelope.getDeliveryTag, body.toSeq)
      )
    end handleDelivery
  end RabbitMqConsumer

end MqManager

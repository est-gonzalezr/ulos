package actors.mq

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import actors.Orchestrator
import akka.Done
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.PreRestart
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import types.MessageQueueConnectionParams
import types.MqManagerSetup
import types.MqMessage
import types.OpaqueTypes.MqExchangeName
import types.OpaqueTypes.MqQueueName
import types.OpaqueTypes.RoutingKey
import types.Task
import utilities.MiscUtils

private val DefaultMqRetries = 10

/** A persistent actor responsible for managing actors with message queue
  * related tasks. It acts as the intermediary between the message queue and the
  * system that processes the tasks.
  */
object MqManager:
  given Timeout = 10.seconds

  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class MqProcessTask(mqMessage: MqMessage) extends Command
  final case class MqAckTask(
      id: Long,
      retries: Int = DefaultMqRetries
  ) extends Command
  final case class MqRejectTask(id: Long, retries: Int = DefaultMqRetries)
      extends Command
  final case class MqSendMessage(
      task: Task,
      exchange: MqExchangeName,
      routingKey: RoutingKey,
      retries: Int = DefaultMqRetries
  ) extends Command

  // Internal command protocol
  private final case class DeliverToOrchestrator(task: Task) extends Command
  private final case class NotifyFatalFailure(th: Throwable) extends Command
  private case object NoOp extends Command

  def apply(
      connParams: MessageQueueConnectionParams,
      consumptionQueue: MqQueueName,
      replyTo: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
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
      replyTo: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.log.info("MqManager started...")

      val behavior = initializeBrokerLink(
        connParams
      ).fold(
        th =>
          context.log.error(
            s"Connection to broker failed. Host --> ${connParams.host.value}, Port --> ${connParams.port.value}. th: ${th.getMessage}"
          )
          context.log.error(
            "Shutting down MqManager."
              + "\nCONTACT SYSTEM ADMINISTRATOR!!!"
              + "\nCONTACT SYSTEM ADMINISTRATOR!!!"
              + "\nCONTACT SYSTEM ADMINISTRATOR!!!"
          )
          Behaviors.stopped
        ,
        (connection, channel) =>
          // val supervisedMqConsumer = Behaviors
          //   .supervise(MqConsumer(channel, consumptionQueue, context.self))
          //   .onFailure(SupervisorStrategy.restart)

          // val mqConsumer = context.spawn(
          //   MqConsumer(channel, consumptionQueue, context.self),
          //   "mq-consumer"
          // )
          val consumer = RabbitMqConsumer(channel, context.self)
          val _ = channel.basicConsume(consumptionQueue.value, false, consumer)
          val setup = MqManagerSetup(connection, channel, replyTo)
          handleMessages(setup)
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
  private def handleMessages(setup: MqManagerSetup): Behavior[Command] =
    Behaviors
      .receive[Command] { (context, message) =>
        message match

          /* **********************************************************************
           * Public commands
           * ********************************************************************** */

          case MqProcessTask(mqMessage) =>
            delegateProcessTask(context, mqMessage)
            Behaviors.same

          case MqAckTask(mqId, retries) =>
            delegateAckTask(context, setup.channel, mqId, retries)
            Behaviors.same

          case MqRejectTask(mqId, retries) =>
            delegateRejectTask(context, setup.channel, mqId, retries)
            Behaviors.same

          case MqSendMessage(task, exchange, routingKey, retries) =>
            delegatePublishTask(
              context,
              setup.channel,
              exchange,
              routingKey,
              task,
              retries
            )
            Behaviors.same

          /* **********************************************************************
           * Internal commands
           * ********************************************************************** */

          case DeliverToOrchestrator(task) =>
            setup.orchestratorRef ! Orchestrator.ProcessTask(task)
            Behaviors.same

          case NoOp =>
            Behaviors.same

          case NotifyFatalFailure(th) =>
            setup.orchestratorRef ! Orchestrator.Fail(th)
            Behaviors.same

        end match
      }
      .receiveSignal {
        case (_, PreRestart) =>
          // context.log.info(
          //   "MqManager stopped. Shutting down Connection and Channel"
          // )
          println("PreRestart")
          setup.connection.close()
          setup.channel.close()
          Behaviors.same
        case any =>
          println("what the f")
          println(any)
          Behaviors.same
      }
  end handleMessages

  /** Send a task to the processing cycle.
    *
    * @param context
    *   The actor context.
    * @param mqMessage
    *   The message queue message.
    *
    * @return
    *   Unit
    */
  private def delegateProcessTask(
      context: ActorContext[Command],
      mqMessage: MqMessage
  ): Unit =
    val supervisedDeserializer =
      Behaviors.supervise(MqTranslator()).onFailure(SupervisorStrategy.stop)

    val deserializer = context.spawnAnonymous(supervisedDeserializer)
    // context.watchWith(deserializer, MqRejectTask(mqMessage.mqId))

    context.askWithStatus[MqTranslator.DeserializeMessage, Task](
      deserializer,
      replyTo => MqTranslator.DeserializeMessage(mqMessage, replyTo)
    ) {
      case Success(task) =>
        DeliverToOrchestrator(task)

      case Failure(_) =>
        MqRejectTask(mqMessage.mqId)
    }
  end delegateProcessTask

  /** Delegate the acknowledgement of a task received from the message queue and
    * handle any errors.
    *
    * @param context
    *   The actor context.
    * @param channel
    *   The channel to communicate with.
    * @param mqId
    *   The message queue ID.
    * @param retries
    *   The number of retries.
    *
    * @return
    *   Unit
    */
  private def delegateAckTask(
      context: ActorContext[Command],
      channel: Channel,
      mqId: Long,
      retries: Int
  ): Unit =

    // val supervisedCommunicator = Behaviors.supervise(MqCommunicator(channel)).onFailure(SupervisorStrategy.stop)
    val communicator = context.spawnAnonymous(MqCommunicator(channel))

    context.askWithStatus[MqCommunicator.AckMessage, Done](
      communicator,
      replyTo => MqCommunicator.AckMessage(mqId, replyTo)
    ) {
      case Success(Done) =>
        NoOp

      case Failure(th) =>
        val failureMessage =
          s"MQ Ack failure for mqId --> $mqId: ${th.getMessage()}"

        MiscUtils.defineRetryCommand(
          context,
          retries,
          failureMessage,
          MqAckTask(mqId, retries - 1),
          NotifyFatalFailure(th)
        )
    }
  end delegateAckTask

  /** Delegate the rejection of a task received from the message queue and
    * handle any errors.
    *
    * @param context
    *   The actor context.
    * @param channel
    *   The channel to communicate with.
    * @param mqId
    *   The message queue ID.
    * @param retries
    *   The number of retries.
    *
    * @return
    *   Unit
    */
  private def delegateRejectTask(
      context: ActorContext[Command],
      channel: Channel,
      mqId: Long,
      retries: Int
  ): Unit =
    val communicator = context.spawnAnonymous(MqCommunicator(channel))

    context.askWithStatus[MqCommunicator.RejectMessage, Done](
      communicator,
      replyTo => MqCommunicator.RejectMessage(mqId, replyTo)
    ) {
      case Success(Done) =>
        NoOp

      case Failure(th) =>
        val failureMessage =
          s"MQ Reject failure for mqId --> $mqId: ${th.getMessage()}"

        MiscUtils.defineRetryCommand(
          context,
          retries,
          failureMessage,
          MqRejectTask(mqId, retries - 1),
          NotifyFatalFailure(th)
        )
    }
  end delegateRejectTask

  /** Delegate the publishing of a task to a message queue and handle any
    * errors.
    *
    * @param context
    *   The actor context.
    * @param channel
    *   The channel to communicate with.
    * @param exchange
    *   The exchange name.
    * @param routingKey
    *   The routing key.
    * @param task
    *   The task to publish.
    * @param retries
    *   The number of retries.
    *
    * @return
    *   Unit
    */
  private def delegatePublishTask(
      context: ActorContext[Command],
      channel: Channel,
      exchange: MqExchangeName,
      routingKey: RoutingKey,
      task: Task,
      retries: Int
  ): Unit =
    val serializer = context.spawnAnonymous(MqTranslator())

    context.askWithStatus[MqTranslator.SerializeMessage, Seq[Byte]](
      serializer,
      replyTo => MqTranslator.SerializeMessage(task, replyTo)
    ) {
      case Success(bytes) =>
        val communicator = context.spawnAnonymous(MqCommunicator(channel))

        context.askWithStatus[MqCommunicator.PublishMessage, Done](
          communicator,
          replyTo =>
            MqCommunicator.PublishMessage(
              bytes,
              exchange,
              routingKey,
              replyTo
            )
        ) {
          case Success(Done) =>
            NoOp

          case Failure(th) =>
            val failureMessage =
              s"MQ Publish failure for Task --> $task, exchange --> ${exchange.value}, routingKey --> ${routingKey.value}: ${th.getMessage()}"

            MiscUtils.defineRetryCommand(
              context,
              retries,
              failureMessage,
              MqSendMessage(
                task,
                exchange,
                routingKey,
                retries - 1
              ),
              NotifyFatalFailure(th)
            )
        }

        NoOp
      case Failure(th) =>
        context.log.error(
          s"Serialization failure. Task --> $task. th: ${th
              .getMessage()}." +
            s"\nCONTACT SYSTEM ADMINISTRATOR!!!." +
            s"\nCONTACT SYSTEM ADMINISTRATOR!!!." +
            s"\nCONTACT SYSTEM ADMINISTRATOR!!!."
        )

        NotifyFatalFailure(th)
    }
  end delegatePublishTask

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
      replyTo ! MqManager.MqProcessTask(
        MqMessage(envelope.getDeliveryTag, body.toSeq)
      )
    end handleDelivery
  end RabbitMqConsumer

end MqManager

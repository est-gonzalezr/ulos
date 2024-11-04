package actors.mq

/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success
import akka.pattern.StatusReply
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory

import types.MqMessage
import types.Task
import types.OpaqueTypes.ExchangeName
import types.OpaqueTypes.RoutingKey

import actors.Orchestrator
import akka.Done

private val DefaultMqRetries = 10

/** This actor manages the actors that are related to the Message Queue. It acts
  * as the intermediary between the MQ and the system that processes the tasks
  * that come from the MQ.
  */
object MqManager:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class MqProcessTask(mqMessage: MqMessage) extends Command
  final case class MqAcknowledgeTask(
      id: String,
      retries: Int = DefaultMqRetries
  ) extends Command
  final case class MqRejectTask(id: String, retries: Int = DefaultMqRetries)
      extends Command
  final case class MqSendMessage(
      task: Task,
      exchange: ExchangeName,
      routingKey: RoutingKey,
      retries: Int = DefaultMqRetries
  ) extends Command
  case object Shutdown extends Command

  // Internal command protocol
  private final case class DeliverToOrchestrator(task: Task) extends Command
  private final case class ReportMqError(str: String) extends Command
  private final case class ReportMqSuccess(str: String) extends Command

  // Implicit timeout for ask pattern
  implicit val timeout: Timeout = 10.seconds

  def apply(ref: ActorRef[Orchestrator.Command]): Behavior[Command] =
    setup(ref)

  def setup(
      ref: ActorRef[Orchestrator.Command]
  ): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.log.info("MqManager started...")

      val connection = brokerConnecton("localhost", 5672, "guest", "guest")
      val channel = connection.createChannel

      // The actor is responsible for starting all other actors that are realted to the MQ messages processing
      val mqConsumer = context.spawn(
        MqConsumer(context.self, channel),
        "mq-consumer"
      )

      def processingMessages(): Behavior[Command] =
        Behaviors.receiveMessage[Command] { message =>
          message match

            /* **********************************************************************
             * Public commands
             * ********************************************************************** */

            case MqProcessTask(mqMessage) =>
              val deserializer = context.spawnAnonymous(MqMessageDeserializer())

              context
                .askWithStatus[MqMessageDeserializer.DeserializeMessage, Task](
                  deserializer,
                  ref =>
                    MqMessageDeserializer.DeserializeMessage(mqMessage, ref)
                ) {
                  case Success(task) =>
                    context.self ! DeliverToOrchestrator(task)
                    ReportMqSuccess("Task processed successfully")

                  case Failure(exception) =>
                    context.self ! MqRejectTask(mqMessage.id)
                    ReportMqError(exception.getMessage)
                }
              Behaviors.same

            case MqAcknowledgeTask(mqId, retries) =>
              val communicator = context.spawnAnonymous(MqCommunicator(channel))

              context.askWithStatus[MqCommunicator.SendAck, Done](
                communicator,
                ref => MqCommunicator.SendAck(mqId, ref)
              ) {
                case Success(Done) =>
                  ReportMqSuccess("Ack successful")

                case Failure(exception) =>
                  if retries > 0 then
                    context.self ! MqAcknowledgeTask(mqId, retries - 1)
                    ReportMqError(s"${exception.getMessage}. Retrying...")
                  else ReportMqError(exception.getMessage)
              }
              Behaviors.same

            case MqRejectTask(mqId, retries) =>
              val communicator = context.spawnAnonymous(MqCommunicator(channel))

              context.askWithStatus[MqCommunicator.SendReject, Done](
                communicator,
                ref => MqCommunicator.SendReject(mqId, ref)
              ) {
                case Success(Done) =>
                  ReportMqSuccess("Reject successful")

                case Failure(exception) =>
                  if retries > 0 then
                    context.self ! MqRejectTask(mqId, retries - 1)
                    ReportMqError(s"${exception.getMessage}. Retrying...")
                  else ReportMqError(exception.getMessage)
              }
              Behaviors.same

            case MqSendMessage(mqMessage, exchange, routingKey, retries) =>
              val serializer = context.spawnAnonymous(MqMessageSerializer())

              context
                .askWithStatus[MqMessageSerializer.SerializeMessage, Seq[Byte]](
                  serializer,
                  ref => MqMessageSerializer.SerializeMessage(mqMessage, ref)
                ) {
                  case Success(bytes) =>
                    val communicator =
                      context.spawnAnonymous(MqCommunicator(channel))

                    context.askWithStatus[MqCommunicator.SendMqMessage, Done](
                      communicator,
                      ref =>
                        MqCommunicator
                          .SendMqMessage(bytes, exchange, routingKey, ref)
                    ) {
                      case Success(Done) =>
                        ReportMqSuccess("Message sent successfully")

                      case Failure(exception) =>
                        if retries > 0 then
                          context.self ! MqSendMessage(
                            mqMessage,
                            exchange,
                            routingKey,
                            retries - 1
                          )
                          ReportMqError(s"${exception.getMessage}. Retrying...")
                        else ReportMqError(exception.getMessage)
                    }

                    ReportMqSuccess("Message serialized successfully")
                  case Failure(exception) =>
                    if retries > 0 then
                      context.self ! MqSendMessage(
                        mqMessage,
                        exchange,
                        routingKey,
                        retries - 1
                      )
                      ReportMqError(s"${exception.getMessage}. Retrying...")
                    else ReportMqError(exception.getMessage)
                }

              Behaviors.same

            case Shutdown =>
              context.log.info("Shutting down MqManager")
              connection.close()
              Behaviors.stopped

            /* **********************************************************************
             * Internal commands
             * ********************************************************************** */

            case DeliverToOrchestrator(task) =>
              ref ! Orchestrator.ProcessTask(task)
              Behaviors.same

            case ReportMqError(errorMessage) =>
              context.log.error(errorMessage)
              Behaviors.same

            case ReportMqSuccess(message) =>
              context.log.info(message)
              Behaviors.same
        }

      processingMessages()
    }

  def brokerConnecton(
      host: String,
      port: Int,
      username: String,
      password: String
  ): Connection =
    val factory = ConnectionFactory()
    factory.setHost(host)
    factory.setPort(port)
    factory.setUsername(username)
    factory.setPassword(password)

    factory.newConnection()
  end brokerConnecton

end MqManager

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

import actors.Orchestrator

/** This actor manages the actors that are related to the Message Queue. It acts
  * as the intermediary between the MQ and the system that processes the tasks
  * that come from the MQ.
  */
object MqManager:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class MqProcessTask(mqMessage: MqMessage) extends Command
  final case class MqAcknowledgeTask(task: Task) extends Command
  final case class MqRejectTask(task: Task) extends Command
  case object Shutdown extends Command

  // Internal command protocol
  private final case class DeserializeMqMessage(mqMessage: MqMessage)
      extends Command
  private final case class SerializeTask(task: Task) extends Command

  private final case class AcknowledgeMqMessage(mqMessage: MqMessage)
      extends Command
  private final case class RejectMqMessage(mqMessage: MqMessage) extends Command

  private final case class DeliverToOrchestrator(task: Task) extends Command
  private final case class ReportMqError(str: String) extends Command

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

      val mqCommunicator = context.spawn(
        MqCommunicator(channel),
        "mq-communicator"
      )

      def processingMessages(): Behavior[Command] =
        Behaviors.receiveMessage[Command] { message =>
          message match

            /* **********************************************************************
             * Public commands
             * ********************************************************************** */

            case MqProcessTask(mqMessage) =>
              // context.log.info(
              //   "Received message from MQ, sending deserialization task to MQ Message Parser"
              // )

              context.self ! DeserializeMqMessage(mqMessage)
              Behaviors.same

            case MqAcknowledgeTask(task) =>
              // context.log.info(
              //   "Received task from Orchestrator, sending serialization task to MQ Message Parser"
              // )

              context.self ! SerializeTask(task)
              Behaviors.same

            case MqRejectTask(task) =>
              // context.log.info(
              //   "Received task from Orchestrator, sending serialization task to MQ Message Parser"
              // )

              context.self ! SerializeTask(task)
              Behaviors.same

            case Shutdown =>
              context.log.info("Shutting down MqManager")
              connection.close()
              Behaviors.stopped

            /* **********************************************************************
             * Internal commands
             * ********************************************************************** */

            case DeserializeMqMessage(mqMessage) =>
              // context.log.info(
              //   "Received message to deserialize, delegating to worker deserializer..."
              // )

              val deserializer = context.spawnAnonymous(MqMessageDeserializer())

              context.askWithStatus[
                MqMessageDeserializer.DeserializeMessage,
                MqMessageDeserializer.MessageDeserialized
              ](
                deserializer,
                ref => MqMessageDeserializer.DeserializeMessage(mqMessage, ref)
              ) {
                case Success(MqMessageDeserializer.MessageDeserialized(task)) =>
                  DeliverToOrchestrator(task)
                case Failure(exception) =>
                  RejectMqMessage(mqMessage)
              }

              Behaviors.same

            case SerializeTask(task) =>
              // context.log.info(
              //   "Received message to serialize, delegating to worker serializer..."
              // )

              val serializer = context.spawnAnonymous(MqMessageSerializer())

              context.askWithStatus[
                MqMessageSerializer.SerializeMessage,
                MqMessageSerializer.MessageSerialized
              ](
                serializer,
                ref => MqMessageSerializer.SerializeMessage(task, ref)
              ) {
                case Success(
                      MqMessageSerializer.MessageSerialized(mqMessage)
                    ) =>
                  AcknowledgeMqMessage(mqMessage)
                case Failure(exception) =>
                  SerializeTask(task)
              }

              Behaviors.same

            case AcknowledgeMqMessage(mqMessage) =>
              // context.log.info("Sending message to MQ")
              mqCommunicator ! MqCommunicator.SendAck(mqMessage)
              Behaviors.same

            case RejectMqMessage(mqMessage) =>
              // context.log.info("Sending message to MQ")
              mqCommunicator ! MqCommunicator.SendReject(mqMessage)
              Behaviors.same

            case DeliverToOrchestrator(task) =>
              // context.log.info("Sending task to Orchestrator")
              ref ! Orchestrator.ProcessTask(task)
              Behaviors.same

            case ReportMqError(exception) =>
              context.log.error(exception)
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

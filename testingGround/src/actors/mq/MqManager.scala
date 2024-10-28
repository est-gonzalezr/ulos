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

/** This actor manages the actors that are related to the Message Queue. It acts
  * as the intermediary between the MQ and the system that processes the tasks
  * that come from the MQ.
  */
object MqManager:
  // Command protocol
  sealed trait Command
  final case class DeserializeMqMessage(mqMessage: MqMessage) extends Command
  final case class SerializeMqMessage(task: Task) extends Command
  final case class DeliverToOrchestrator(task: Task) extends Command
  final case class AcknowledgeMessage(mqMessage: MqMessage) extends Command
  final case class RejectMessage(mqMessage: MqMessage) extends Command
  final case class ReportMqError(str: String) extends Command

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
            case DeserializeMqMessage(mqMessage) =>
              context.log.info(
                "Received message from MQ, sending deserialization task to MQ Message Parser"
              )

              // the MQ Parser is instantiated everytime a message is received since it is a stateless actor
              val mqParser =
                context.spawnAnonymous(MqParser())

              context.askWithStatus[
                MqParser.DeserializeMessage,
                MqParser.MessageDeserialized
              ](
                mqParser,
                ref => MqParser.DeserializeMessage(mqMessage, ref)
              ) {
                case Success(MqParser.MessageDeserialized(task)) =>
                  DeliverToOrchestrator(task)
                case Failure(StatusReply.ErrorMessage(error)) =>
                  context.log.info(error)
                  RejectMessage(mqMessage)
                case Failure(throwable) =>
                  context.log.error(throwable.toString)
                  RejectMessage(mqMessage)
              }

              processingMessages()

            case SerializeMqMessage(task) =>
              context.log.info(
                "Received task from Orchestrator, sending serialization task to MQ Message Parser"
              )

              val mqParser =
                context.spawnAnonymous(MqParser())

              context.askWithStatus[
                MqParser.SerializeMessage,
                MqParser.MessageSerialized
              ](
                mqParser,
                ref => MqParser.SerializeMessage(task, ref)
              ) {
                case Success(MqParser.MessageSerialized(mqMessage)) =>
                  AcknowledgeMessage(mqMessage)
                case Failure(exception) =>
                  ReportMqError(exception.toString)
              }

              processingMessages()

            case DeliverToOrchestrator(task) =>
              context.log.info("Sending task to Orchestrator")
              ref ! Orchestrator.ProcessTask(task)
              processingMessages()

            case AcknowledgeMessage(mqMessage) =>
              context.log.info("Sending message to MQ")
              mqCommunicator ! MqCommunicator.SendAck(mqMessage)
              processingMessages()

            case RejectMessage(mqMessage) =>
              context.log.info("Sending message to MQ")
              mqCommunicator ! MqCommunicator.SendReject(mqMessage)
              processingMessages()

            case ReportMqError(exception) =>
              // ref ! s"Exception: $exception"
              processingMessages()
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

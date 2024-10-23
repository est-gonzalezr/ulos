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
  final case class DeliverToMqAck(mqMessage: MqMessage) extends Command
  final case class DeliverToMqNack(mqMessage: MqMessage) extends Command
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

      // The actor is responsible for starting all other actors that are realted to the MQ messages processing
      val mqConsumer = context.spawn(
        MqConsumer(context.self, connection.createChannel),
        "mq-consumer"
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
                  ReportMqError(error)
                case Failure(exception) =>
                  ReportMqError(exception.toString)
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
                  DeliverToMqAck(mqMessage)
                case Failure(exception) =>
                  ReportMqError(exception.toString)
              }

              processingMessages()

            case DeliverToOrchestrator(taskType) =>
              context.log.info("Sending task to Orchestrator")
              ref ! Orchestrator.ProcessTask("cypress")
              processingMessages()

            case DeliverToMqAck(bytes) =>
              context.log.info("Sending message to MQ")
              // mqConsumer ! MqConsumer.DeliverMessage(bytes)
              processingMessages()

            case DeliverToMqNack(bytes) =>
              context.log.info("Sending message to MQ")
              // mqConsumer ! MqConsumer.DeliverMessage(bytes)
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

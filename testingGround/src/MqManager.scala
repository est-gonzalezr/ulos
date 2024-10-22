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

/** This actor manages the actors that are related to the Message Queue. It acts
  * as the intermediary between the MQ and the system that processes the tasks
  * that come from the MQ.
  */
object MqManager:
  // Command protocol
  sealed trait Command
  final case class DeserializeMqMessage(bytes: Seq[Byte]) extends Command
  final case class SerializeMqMessage(taskInfo: String) extends Command
  final case class DeliverToOrchestrator(taskType: String) extends Command
  final case class DeliverToMq(bytes: Seq[Byte]) extends Command
  final case class ReportException(exception: Throwable) extends Command

  // Implicit timeout for ask pattern
  implicit val timeout: Timeout = 10.seconds

  def apply(ref: ActorRef[Orchestrator.Command]): Behavior[Command] =
    processing(ref)

  def processing(
      ref: ActorRef[Orchestrator.Command],
      activeWorkers: Int = 0
  ): Behavior[Command] =
    Behaviors.setup[Command] { context =>
      context.log.info("MqManager started...")

      // The actor is responsible for starting all other actors that are realted to the MQ messages processing
      val mqConsumer = context.spawn(MqConsumer(context.self), "mq-consumer")

      Behaviors.receiveMessage[Command] { message =>
        message match
          case DeserializeMqMessage(bytes) =>
            context.log.info(
              "Received message from MQ, sending deserialization task to MQ Message Parser"
            )

            // the MQ Parser is instantiated everytime a message is received since it is a stateless actor
            val mqParser =
              context.spawn(MqMessageParser(), s"mq-parser-$activeWorkers")

            context.askWithStatus(
              mqParser,
              ref => MqMessageParser.DeserializeMessage(bytes, ref)
            ) {
              case Success(MqMessageParser.MessageDeserialized(taskInfo)) =>
                DeliverToOrchestrator(taskInfo)
              case Success(MqMessageParser.DeserializationFailed) =>
                ReportException(Exception("Deserialization failed"))
              case Failure(_) =>
                ReportException(Exception("Deserialization failed"))
            }

            context.ask[
              MqMessageParser.Command,
              MqMessageParser.DeserializationResponse
            ](
              mqParser,
              ref => MqMessageParser.DeserializeMessage(bytes, ref)
            ) {
              case Success(MqMessageParser.MessageDeserialized(taskInfo)) =>
                DeliverToOrchestrator(taskInfo)
              case Success(MqMessageParser.DeserializationFailed) =>
                ReportException(Exception("Deserialization failed"))
              case Failure(_) =>
                ReportException(Exception("Deserialization failed"))
            }

            processing(ref, activeWorkers + 1)

          case SerializeMqMessage(taskInfo) =>
            context.log.info(
              "Received task from Orchestrator, sending serialization task to MQ Message Parser"
            )
            val mqParser =
              context.spawn(MqMessageParser(), s"mq-parser-$activeWorkers")

            context.ask[
              MqMessageParser.Command,
              MqMessageParser.SerializationResponse
            ](
              mqParser,
              ref => MqMessageParser.SerializeMessage(taskInfo, ref)
            ) {
              case Success(MqMessageParser.MessageSerialized(bytes)) =>
                DeliverToMq(bytes)
              case _ =>
                ReportException(Exception("Serialization failed"))
            }

            processing(ref, activeWorkers + 1)

          case DeliverToOrchestrator(taskType) =>
            context.log.info("Sending task to Orchestrator")
            ref ! Orchestrator.ProcessTask("cypress")
            processing(ref, activeWorkers - 1)

          case DeliverToMq(bytes) =>
            context.log.info("Sending message to MQ")
            // mqConsumer ! MqConsumer.DeliverMessage(bytes)
            processing(ref, activeWorkers - 1)

          case ProcessTask(taskType, taskPath) =>
            context.log.info("Sending task to Orchestrator")
            ref ! Orchestrator.ProcessTask("cypress")
            Behaviors.same

          case ReportException(exception) =>
            // ref ! s"Exception: $exception"
            Behaviors.same
      }
    }
  end processing
end MqManager

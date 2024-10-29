package actors.mq

/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import types.MqMessage
import types.Task
import zio.json.*

/** This actor is responsible for deseralizing the messages that come from the
  * Message Queue. It is a stateless actor that is instantiated everytime a
  * message is received.
  */
object MqMessageDeserializer:
  // Command protocol
  sealed trait Command
  final case class DeserializeMessage(
      mqMessage: MqMessage,
      ref: ActorRef[StatusReply[MessageDeserialized]]
  ) extends Command

  // Response protocol
  sealed trait Response
  final case class MessageDeserialized(task: Task) extends Response

  def apply(): Behavior[Command] = deserializing()

  /** This behavior represents the deserializing state of the actor.
    *
    * @return
    *   A behavior that deserializes a message and sends it to the MQ Manager.
    */
  def deserializing(): Behavior[Command] =
    Behaviors.receive[Command] { (context, message) =>
      message match
        case DeserializeMessage(mqMessage, ref) =>
          context.log.info(
            "Message received from MQ Manager, deserializing..."
          )

          mqMessageAsTask(mqMessage) match
            case Right(task) =>
              ref ! StatusReply.Success(MessageDeserialized(task))
              context.log.info(
                "Message deserialized, sent to MQ Manager"
              )
            case Left(error) =>
              ref ! StatusReply.Error(s"Deserialization failed: $error")
              context.log.error(
                "Deserialization failed, sent response to MQ Manager"
              )
          end match
      end match

      Behaviors.stopped
    }

  /** Deserializes a message from the Message Queue.
    *
    * @param mqMessage
    *   The message to be deserialized.
    * @return
    *   Either a Task or an error message.
    */
  def mqMessageAsTask(mqMessage: MqMessage): Either[String, Task] =
    mqMessage.bytes
      .map(_.toChar)
      .mkString
      .fromJson[Task]
      .map(_.copy(mqId = mqMessage.id))

end MqMessageDeserializer

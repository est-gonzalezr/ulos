/** @author
  *   Esteban Gonzalez Ruales
  */

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply

/** This actor is responsible for parsing the messages that come from the
  * Message Queue. It is a stateless actor that is instantiated everytime a
  * message is received.
  */
object MqMessageParser:
  // Command protocol
  sealed trait Command
  final case class DeserializeMessage(
      bytes: Seq[Byte],
      ref: ActorRef[StatusReply[MessageDeserialized]]
  ) extends Command
  final case class SerializeMessage(
      taskInfo: String,
      ref: ActorRef[StatusReply[MessageSerialized]]
  ) extends Command

  // Response protocol
  sealed trait Response
  final case class MessageDeserialized(taskInfo: String) extends Response
  final case class MessageSerialized(bytes: Seq[Byte]) extends Response

  def apply(): Behavior[Command] = parsing

  /** This behavior represents the parsing state of the actor.
    *
    * @return
    *   A behavior that parses a message and sends it to the MQ Manager.
    */
  def parsing: Behavior[Command] =
    Behaviors.receive[Command] { (context, message) =>
      message match
        case DeserializeMessage(bytes, ref) =>
          context.log.info(
            "Message received from MQ Manager, deserializing..."
          )
          deserializedMessage(bytes) match
            case Right(taskInfo) =>
              context.log.info(
                "Message deserialized, sending to MQ Manager"
              )
              ref ! StatusReply.Success(MessageDeserialized(taskInfo))
            case Left(error) =>
              context.log.error(
                "Deserialization failed, sending response to MQ Manager"
              )
              ref ! StatusReply.Error(s"Deserialization failed: $error")
          end match

          Behaviors.same

        case SerializeMessage(taskInfo, ref) =>
          context.log.info(
            s"Message received MQ Manager, serializing..."
          )
          serializedMessage(taskInfo) match
            case Right(bytes) =>
              context.log.info(
                "Message serialized, sending to MQ"
              )
              ref ! StatusReply.Success(MessageSerialized(bytes))
            case Left(error) =>
              context.log.error(
                "Serialization failed, sending response to system"
              )
              ref ! StatusReply.Error(s"Serialization failed: $error")
          end match

          Behaviors.same
    }

  /** Deserializes a message from a sequence of bytes to an object
    *
    * @param bytes
    *   The sequence of bytes that represents the message
    * @return
    *   Either an object with the deserialized message or an error message
    */
  private def deserializedMessage(bytes: Seq[Byte]): Either[String, String] =
    Right(bytes.map(_.toChar).mkString)

  /** Serializes a message from an object to a sequence of bytes
    *
    * @param task
    *   The object to be serialized
    * @return
    *   Either a sequence of bytes or an error message
    */
  private def serializedMessage(taskInfo: String): Either[String, Seq[Byte]] =
    Right(taskInfo.map(_.toByte).toSeq)
end MqMessageParser

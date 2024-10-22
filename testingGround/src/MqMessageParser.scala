import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern.*
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.duration.*
import scala.util.Failure
import scala.util.Success

object MqMessageParser:
  sealed trait Command
  final case class DeserializeMessage(
      body: Seq[Byte],
      ref: ActorRef[Response]
  ) extends Command
  final case class SerializeMessage(
      str: String,
      ref: ActorRef[Response]
  ) extends Command

  sealed trait Response
  final case class MessageDeserialized(str: String) extends Response
  final case class MessageSerialized(body: Seq[Byte]) extends Response

  def apply(): Behavior[Command] = parsing

  def parsing: Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match
        case DeserializeMessage(body, ref) =>
          val msg = deserializedMessage(body)
          context.log.info(
            s"Deserialized message: $msg"
          )
          ref ! MessageDeserialized(msg)
          Behaviors.same

        case SerializeMessage(str, ref) =>
          val msg = serializedMessage(str)
          context.log.info(
            s"Serialized message: $msg"
          )
          ref ! MessageSerialized(msg)
          Behaviors.same
    }

  private def deserializedMessage(body: Seq[Byte]): String =
    body.map(_.toChar).mkString

  private def serializedMessage(str: String): Seq[Byte] =
    str.getBytes.map(_.toByte).toSeq
end MqMessageParser

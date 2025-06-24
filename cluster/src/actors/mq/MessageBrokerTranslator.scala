package actors.mq

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.pattern.StatusReply
import types.Task
import zio.json.*

/** A stateless actor responsible for serializing and deserializing messages
  * received from the message queue. A new instance is created for each message.
  */
object MessageBrokerTranslator:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class SerializeMessage(
      task: Task,
      replyTo: ActorRef[StatusReply[Seq[Byte]]]
  ) extends Command
  final case class DeserializeMessage(
      bytes: Seq[Byte],
      replyTo: ActorRef[StatusReply[Task]]
  ) extends Command

  def apply(): Behavior[Command] = translate()

  /** Handles the translation of messagess from and to the message queue.
    *
    * @return
    *   A Behavior that handles message translation by serializing or
    *   deserializing data and returning the result.
    */
  private def translate(): Behavior[Command] =
    Behaviors.receive[Command] { (_, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        case SerializeMessage(task, replyTo) =>
          replyTo ! StatusReply.Success(convertTaskToBytes(task))

        case DeserializeMessage(bytes, replyTo) =>
          replyTo ! convertMqMessageToTask(bytes).fold(
            StatusReply.Error(_),
            StatusReply.Success(_)
          )

      end match

      Behaviors.stopped
    }
  end translate

  /** Serializes the given Task into a byte array.
    *
    * @param task
    *   The task to be serialized.
    *
    * @return
    *   The serialized message as bytes.
    */
  private def convertTaskToBytes(task: Task): Seq[Byte] =
    task.toJson.map(_.toByte)

  /** Deserializes the given message received from the message queue.
    *
    * @param mqMessage
    *   The bytes to be deserialized.
    *
    * @return
    *   Either a Task or an error message.
    */
  private def convertMqMessageToTask(
      bytes: Seq[Byte]
  ): Either[String, Task] =
    bytes
      .map(_.toChar)
      .mkString
      .fromJson[Task]
end MessageBrokerTranslator

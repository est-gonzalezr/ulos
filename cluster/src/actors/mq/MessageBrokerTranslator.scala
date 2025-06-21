package actors.mq

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply
import types.MqMessage
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
      mqMessage: MqMessage,
      replyTo: ActorRef[StatusReply[Task]]
  ) extends Command

  def apply(): Behavior[Command] = translate()

  /** Handles the translation of messagess from and to the message queue.
    *
    * @return
    *   A Behavior that handles message translation by serializing or
    *   deserializing data and forwarding it to the MQManager.
    */
  private def translate(): Behavior[Command] =
    Behaviors.receive[Command] { (_, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        /* ------------------------------- SerializeMessage ------------------------------- */

        case SerializeMessage(task, replyTo) =>
          replyTo ! StatusReply.Success(convertTaskToBytes(task))

        /* ------------------------------- DeserializeMessage ------------------------------- */

        case DeserializeMessage(mqMessage, replyTo) =>
          replyTo ! convertMqMessageToTask(mqMessage).fold(
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
    *   The serialized message.
    */
  private def convertTaskToBytes(task: Task): Seq[Byte] =
    task.toJson.map(_.toByte)

  /** Deserializes the given message received from the message queue.
    *
    * @param mqMessage
    *   The message to be deserialized.
    *
    * @return
    *   Either a Task or an error message.
    */
  private def convertMqMessageToTask(
      mqMessage: MqMessage
  ): Either[String, Task] =
    mqMessage.bytes
      .map(_.toChar)
      .mkString
      .fromJson[Task]
      .map(task => task.copy(mqId = mqMessage.mqId))

end MessageBrokerTranslator

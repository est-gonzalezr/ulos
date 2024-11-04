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

/** This actor is responsible for serializing the messages that come from the
  * system. It is a stateless actor that is instantiated everytime a message is
  * received.
  */
object MqMessageSerializer:
  // Command protocol
  sealed trait Command

  // Public command protocol
  final case class SerializeMessage(
      task: Task,
      ref: ActorRef[StatusReply[Seq[Byte]]]
  ) extends Command

  def apply(): Behavior[Command] = serializing()

  /** This behavior represents the serializing state of the actor.
    *
    * @return
    *   A behavior that serializes a message and sends it to the MQ Manager.
    */
  def serializing(): Behavior[Command] =
    Behaviors.receive[Command] { (context, message) =>
      message match
        case SerializeMessage(task, ref) =>
          context.log.info(
            s"Message received MQ Manager, serializing..."
          )
          ref ! StatusReply.Success(taskAsBytes(task))
      end match

      Behaviors.stopped
    }

  /** Serializes a task into bytes.
    *
    * @param task
    *   The task to be serialized.
    * @return
    *   The serialized message.
    */
  def taskAsBytes(task: Task): Seq[Byte] =
    task.toJson.map(_.toByte)

end MqMessageSerializer

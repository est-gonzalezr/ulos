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

/** This actor is responsible for serlializing and deserializing the messages
  * that come from the Message Queue. It is a stateless actor that is
  * instantiated everytime a message is received.
  */
object MqMessageConverter:
  // command protocol
  sealed trait Command

  // public command protocol
  final case class SerializeMessage(
      task: Task,
      replyTo: ActorRef[StatusReply[Seq[Byte]]]
  ) extends Command

  final case class DeserializeMessage(
      mqMessage: MqMessage,
      replyTo: ActorRef[StatusReply[Task]]
  ) extends Command

  def apply(): Behavior[Command] = converting()

  /** This behavior represents the converting state of the actor.
    *
    * @return
    *   A behavior that serializes or deserializes a message and sends it to the
    *   MQ Manager.
    */
  def converting(): Behavior[Command] =
    Behaviors.receive[Command] { (context, message) =>
      message match

        /* **********************************************************************
         * Public commands
         * ********************************************************************** */

        /* ------------------------------- SerializeMessage ------------------------------- */

        case SerializeMessage(task, replyTo) =>
          context.log.info(
            s"MqConverter received task with id ${task.taskId}. Serializing..."
          )
          replyTo ! StatusReply.Success(taskAsBytes(task))
          context.log.info(
            s"Task with id ${task.taskId} serialized. Bytes sent to MQ Manager"
          )

        /* ------------------------------- DeserializeMessage ------------------------------- */

        case DeserializeMessage(mqMessage, replyTo) =>
          context.log.info(
            s"MqConverter received message with id: ${mqMessage.id}. Deserializing..."
          )

          mqMessageAsTask(mqMessage) match
            case Right(task) =>
              replyTo ! StatusReply.Success(task)
              context.log.info(
                s"Message with id ${mqMessage.id} deserialized. Task sent to MQ Manager."
              )
            case Left(error) =>
              context.log.error(
                s"Deserialization failed for task with id: ${mqMessage.id}. Error: $error. Failure sent to MQ Manager."
              )
              replyTo ! StatusReply.Error(s"Deserialization failed: $error")
          end match
      end match

      Behaviors.stopped
    }
  end converting

  /** Serializes a task into bytes.
    *
    * @param task
    *   The task to be serialized.
    * @return
    *   The serialized message.
    */
  def taskAsBytes(task: Task): Seq[Byte] =
    task.toJson.map(_.toByte)

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
      .map(_.copy(mqId = Some(mqMessage.id)))

end MqMessageConverter

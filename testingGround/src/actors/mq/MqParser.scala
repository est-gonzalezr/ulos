// package actors.mq

// /** @author
//   *   Esteban Gonzalez Ruales
//   */

// import akka.actor.typed.ActorRef
// import akka.actor.typed.Behavior
// import akka.actor.typed.scaladsl.Behaviors
// import akka.pattern.StatusReply

// import zio.json.*

// /** This actor is responsible for parsing the messages that come from the
//   * Message Queue. It is a stateless actor that is instantiated everytime a
//   * message is received.
//   */
// object MqParser:
//   // Command protocol
//   sealed trait Command
//   final case class DeserializeMessage(
//       mqMessage: MqMessage,
//       ref: ActorRef[StatusReply[MessageDeserialized]]
//   ) extends Command
//   final case class SerializeMessage(
//       task: Task,
//       ref: ActorRef[StatusReply[MessageSerialized]]
//   ) extends Command

//   // Response protocol
//   sealed trait Response
//   final case class MessageDeserialized(task: Task) extends Response
//   final case class MessageSerialized(mqMessage: MqMessage) extends Response

//   def apply(): Behavior[Command] = parsing()

//   /** This behavior represents the parsing state of the actor.
//     *
//     * @return
//     *   A behavior that parses a message and sends it to the MQ Manager.
//     */
//   def parsing(): Behavior[Command] =
//     Behaviors.receive[Command] { (context, message) =>
//       message match
//         case DeserializeMessage(mqMessage, ref) =>
//           context.log.info(
//             "Message received from MQ Manager, deserializing..."
//           )
//           deserializedMessage(mqMessage) match
//             case Right(task) =>
//               context.log.info(
//                 "Message deserialized, sending to MQ Manager"
//               )
//               ref ! StatusReply.Success(MessageDeserialized(task))
//             case Left(error) =>
//               context.log.error(
//                 "Deserialization failed, sending response to MQ Manager"
//               )
//               ref ! StatusReply.Error(s"Deserialization failed: $error")
//           end match

//           Behaviors.same

//         case SerializeMessage(task, ref) =>
//           context.log.info(
//             s"Message received MQ Manager, serializing..."
//           )
//           ref ! StatusReply.Success(MessageSerialized(serializedMessage(task)))

//           Behaviors.same
//     }

//   /** Deserializes a message from the message queue
//     *
//     * @param mqMessage
//     *   The message to be deserialized
//     *
//     * @return
//     *   Either a Task object or an error message
//     */
//   private def deserializedMessage(mqMessage: MqMessage): Either[String, Task] =
//     mqMessage.bytes
//       .map(_.toChar)
//       .mkString
//       .fromJson[Task]
//       .map(_.copy(mqId = mqMessage.id))

//   /** Serializes a task into a message to be sent to the message queue
//     *
//     * @param task
//     *   The task to be serialized
//     *
//     * @return
//     *   A MqMessage object
//     */
//   private def serializedMessage(task: Task): MqMessage =
//     MqMessage(task.mqId, task.toJson.map(_.toByte))

// end MqParser

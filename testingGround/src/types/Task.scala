/** @author
  *   Esteban Gonzalez Ruales
  */

import zio.json.JsonDecoder
import zio.json.DeriveJsonDecoder
import zio.json.JsonEncoder
import zio.json.DeriveJsonEncoder
// executed necessary because a machine can fail while it is being executed, so we need to know if it was executed or not

/** This class represents a task that is going to be executed by the system.
  *
  * @param taskId
  *   The unique identifier of the task
  * @param taskOwnerId
  *   The unique identifier of the owner of the task
  * @param storageTaskPath
  *   The path where the task is stored
  * @param parsed
  *   If the task has been parsed
  * @param executed
  *   If the task has been executed
  * @param message
  *   Any message that should be sent to the owner of the task
  * @param mqId
  *   The unique identifier of the message in the Message Queue
  */
case class Task(
    taskId: String,
    taskOwnerId: String,
    taskType: String,
    storageTaskPath: String,
    parsed: Boolean,
    executed: Boolean,
    message: Option[String],
    mqId: String
)

/** Companion object for the Task class. It contains the JSON encoders and
  * decoders.
  */
object Task:
  implicit val decoder: JsonDecoder[Task] = DeriveJsonDecoder.gen[Task]
  implicit val encoder: JsonEncoder[Task] = DeriveJsonEncoder.gen[Task]
end Task

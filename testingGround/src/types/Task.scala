package types

/** @author
  *   Esteban Gonzalez Ruales
  */

import zio.json.JsonDecoder
import zio.json.DeriveJsonDecoder
import zio.json.JsonEncoder
import zio.json.DeriveJsonEncoder
// executed necessary because a machine can fail while it is being executed, so we need to know if it was executed or not

/** This class represents a task that is going to be executed by the system.
  */
case class Task(
    taskId: String,
    taskOwnerId: String,
    taskType: String,
    taskUri: String,
    processingStage: String,
    currentStagePassed: Boolean = false,
    errorMessage: Option[String],
    mqId: Option[String]
)

/** Companion object for the Task class. It contains the JSON encoders and
  * decoders.
  */
object Task:
  implicit val decoder: JsonDecoder[Task] = DeriveJsonDecoder.gen[Task]
  implicit val encoder: JsonEncoder[Task] = DeriveJsonEncoder.gen[Task]
end Task

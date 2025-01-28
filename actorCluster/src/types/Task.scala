package types

/** @author
  *   Esteban Gonzalez Ruales
  */

import zio.json.DeriveJsonDecoder
import zio.json.DeriveJsonEncoder
import zio.json.JsonDecoder
import zio.json.JsonEncoder
// executed necessary because a machine can fail while it is being executed, so we need to know if it was executed or not

/** This class represents a task that is going to be executed by the system.
  */
case class Task(
    taskId: String,
    taskOwnerId: String,
    taskType: String,
    taskPath: String,
    processingStages: List[String],
    logMessage: Option[String],
    mqId: Long = -1
):

  override def toString: String =
    s"Task(taskId=$taskId, taskOwnerId=$taskOwnerId, taskType=$taskType, taskPath=$taskPath, processingStage=$processingStages, logMessage=$logMessage, mqId=$mqId)"
end Task

/** Companion object for the Task class. It contains the JSON encoders and
  * decoders.
  */
object Task:
  implicit val decoder: JsonDecoder[Task] = DeriveJsonDecoder.gen[Task]
  implicit val encoder: JsonEncoder[Task] = DeriveJsonEncoder.gen[Task]
end Task

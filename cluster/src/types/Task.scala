package types

/** @author
  *   Esteban Gonzalez Ruales
  */

import os.Path
import zio.json.DeriveJsonDecoder
import zio.json.DeriveJsonEncoder
import zio.json.JsonDecoder
import zio.json.JsonEncoder

/** This class represents a task that is going to be executed by the system.
  */
case class Task(
    taskId: String,
    taskOwnerId: String,
    taskDefinition: String,
    filePath: Path,
    stages: List[Tuple2[String, Path]],
    logMessage: Option[String],
    mqId: Long = -1
):

  override def toString: String =
    s"Task(taskId=$taskId, taskOwnerId=$taskOwnerId, taskDefinition=$taskDefinition, filePath=$filePath, stages=$stages, logMessage=$logMessage, mqId=$mqId)"
end Task

/** Companion object for the Task class. It contains the JSON encoders and
  * decoders.
  */
object Task:
  implicit val relPathDecoder: JsonDecoder[Path] =
    JsonDecoder[String].map(os.Path(_))
  implicit val taskDecoder: JsonDecoder[Task] = DeriveJsonDecoder.gen[Task]

  implicit val relPathEncoder: JsonEncoder[Path] =
    JsonEncoder[String].contramap(_.toString)
  implicit val taskEncoder: JsonEncoder[Task] = DeriveJsonEncoder.gen[Task]
end Task

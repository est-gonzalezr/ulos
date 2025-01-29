package types

/** @author
  *   Esteban Gonzalez Ruales
  */

import os.RelPath
import zio.json.DeriveJsonDecoder
import zio.json.DeriveJsonEncoder
import zio.json.JsonDecoder
import zio.json.JsonEncoder

/** This class represents a task that is going to be executed by the system.
  */
case class Task(
    taskId: String,
    taskOwnerId: String,
    taskType: String,
    filePath: RelPath,
    containerImagesPaths: List[RelPath],
    processingStages: List[String],
    logMessage: Option[String],
    mqId: Long = -1
):

  override def toString: String =
    s"Task(taskId=$taskId, taskOwnerId=$taskOwnerId, taskType=$taskType, filePath=$filePath, containerImagesPaths=$containerImagesPaths, processingStages=$processingStages, logMessage=$logMessage, mqId=$mqId)"
end Task

/** Companion object for the Task class. It contains the JSON encoders and
  * decoders.
  */
object Task:
  implicit val relPathDecoder: JsonDecoder[RelPath] =
    JsonDecoder[String].map(os.RelPath(_))
  implicit val taskDecoder: JsonDecoder[Task] = DeriveJsonDecoder.gen[Task]

  implicit val relPathEncoder: JsonEncoder[RelPath] =
    JsonEncoder[String].contramap(_.toString)
  implicit val taskEncoder: JsonEncoder[Task] = DeriveJsonEncoder.gen[Task]
end Task

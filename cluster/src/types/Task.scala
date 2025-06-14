package types

/**
 * @author
 *   Esteban Gonzalez Ruales
 */

import scala.concurrent.duration.*

import akka.util.Timeout
import os.Path
import os.RelPath
import zio.json.DeriveJsonDecoder
import zio.json.DeriveJsonEncoder
import zio.json.JsonDecoder
import zio.json.JsonEncoder

/**
 * This class represents a task that is going to be executed by the system.
 */
final case class Task(
  taskId: String,
  taskOwnerId: String,
  filePath: Path,
  timeout: Timeout = 300.seconds,
  routingKeys: List[String],
  logMessage: Option[String],
  mqId: Long = -1,
):

  override def toString: String =
    s"Task(taskId=$taskId, taskOwnerId=$taskOwnerId, filePath=$filePath, queueRoutingKeys=$routingKeys, logMessage=$logMessage, mqId=$mqId)"

  def relTaskFilePath: RelPath = filePath.relativeTo(os.root)
end Task

/**
 * Companion object for the Task class. It contains the JSON encoders and decoders.
 */
object Task:
  implicit val pathDecoder: JsonDecoder[Path] =
    JsonDecoder[String].map(os.Path(_))
  implicit val timeoutDecoder: JsonDecoder[Timeout] =
    JsonDecoder[Long].map(sec => Timeout(sec.seconds))
  implicit val taskDecoder: JsonDecoder[Task] = DeriveJsonDecoder.gen[Task]

  implicit val pathEncoder: JsonEncoder[Path] =
    JsonEncoder[String].contramap(_.toString)
  implicit val timeoutEncoder: JsonEncoder[Timeout] =
    JsonEncoder[Long].contramap(_.duration.toSeconds.toInt)
  implicit val taskEncoder: JsonEncoder[Task] = DeriveJsonEncoder.gen[Task]
end Task

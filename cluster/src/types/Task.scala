package types

import org.apache.pekko.util.Timeout
import os.Path
import os.RelPath
import zio.json.DeriveJsonDecoder
import zio.json.DeriveJsonEncoder
import zio.json.JsonDecoder
import zio.json.JsonEncoder

import scala.concurrent.duration.*

/** This class represents a task that is going to be executed by the system.
  */
final case class Task(
    taskId: String,
    taskOwnerId: String,
    filePath: Path,
    timeout: Timeout,
    routingTree: Option[RoutingDecision],
    logMessage: Option[String],
    mqId: Long = -1
):

  def relTaskFilePath: RelPath = filePath.relativeTo(os.root)
  def relTaskFileDir: RelPath =
    relTaskFilePath / os.up / relTaskFilePath.baseName
  override def toString: String =
    s"Task(taskId=$taskId, filePath=$filePath, timeout=$timeout, mqId=$mqId, routingTree=$routingTree, logMessage=$logMessage)"
end Task

/** Companion object for the Task class. It contains the JSON encoders and
  * decoders.
  */
object Task:
  given JsonDecoder[Path] = JsonDecoder[String].map(os.Path(_))
  given JsonDecoder[Timeout] =
    JsonDecoder[Long].map(sec => Timeout(sec.seconds))
  given JsonDecoder[Task] = DeriveJsonDecoder.gen[Task]

  given JsonEncoder[Path] = JsonEncoder[String].contramap(_.toString)
  given JsonEncoder[Timeout] =
    JsonEncoder[Long].contramap(_.duration.toSeconds.toInt)
  given JsonEncoder[Task] = DeriveJsonEncoder.gen[Task]
end Task

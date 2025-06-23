package types

import org.apache.pekko.util.Timeout
import os.Path
import os.RelPath
import types.OpaqueTypes.MessageBrokerExchange
import types.OpaqueTypes.MessageBrokerRoutingKey
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
    routingKeys: List[(MessageBrokerExchange, MessageBrokerRoutingKey)],
    logMessage: Option[String],
    mqId: Long = -1
):
  override def toString: String =
    s"Task(taskId=$taskId, taskOwnerId=$taskOwnerId, filePath=$filePath, routingKeys=$routingKeys, logMessage=$logMessage, mqId=$mqId)"

  def relTaskFilePath: RelPath = filePath.relativeTo(os.root)
  def relTaskFileDir: RelPath =
    relTaskFilePath / os.up / relTaskFilePath.baseName
end Task

/** Companion object for the Task class. It contains the JSON encoders and
  * decoders.
  */
object Task:
  given JsonDecoder[Path] = JsonDecoder[String].map(os.Path(_))
  given JsonDecoder[Timeout] =
    JsonDecoder[Long].map(sec => Timeout(sec.seconds))
  given JsonDecoder[MessageBrokerExchange] =
    JsonDecoder[String].map(MessageBrokerExchange(_))
  given JsonDecoder[MessageBrokerRoutingKey] =
    JsonDecoder[String].map(MessageBrokerRoutingKey(_))
  given JsonDecoder[Task] = DeriveJsonDecoder.gen[Task]

  given JsonEncoder[Path] = JsonEncoder[String].contramap(_.toString)
  given JsonEncoder[Timeout] =
    JsonEncoder[Long].contramap(_.duration.toSeconds.toInt)
  given JsonEncoder[MessageBrokerExchange] =
    JsonEncoder[String].contramap(_.value)
  given JsonEncoder[MessageBrokerRoutingKey] =
    JsonEncoder[String].contramap(_.value)
  given JsonEncoder[Task] = DeriveJsonEncoder.gen[Task]
end Task

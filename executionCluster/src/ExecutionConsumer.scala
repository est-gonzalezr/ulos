/** @author
  *   Esteban Gonzalez Ruales
  */

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import org.virtuslab.yaml.YamlError
import types.OpaqueTypes.RoutingKey
import types.ProcessingConsumer
import types.StateTypes.*
import types.TaskInfo

case class ExecutionConsumer(
    channel: Channel,
    successRoutingKey: RoutingKey,
    databaseRoutingKey: RoutingKey,
    publishFunction: (RoutingKey, Seq[Byte]) => Unit
) extends DefaultConsumer(channel),
      ProcessingConsumer:

  override def handleDelivery(
      consumerTag: String,
      envelope: Envelope,
      properties: BasicProperties,
      body: Array[Byte]
  ): Unit =

    val deliveryTag = envelope.getDeliveryTag
    val logInfo = logConsumerInfo(consumerTag, deliveryTag)
    val logError = logConsumerError(consumerTag, deliveryTag)
    val logSuccess = logConsumerSuccess(consumerTag, deliveryTag)

    val processingIO = for
      _ <- logInfo(
        s"Message received"
      )
      taskInfo <- IO.fromEither(deserializeMessage(body.toSeq))
      _ <- logInfo("Deserialization success")
      updatedTaskInfo <- processMessage(taskInfo)
      _ <- logInfo("Parsing job completed")
      _ <- sendToFinzalization(updatedTaskInfo)
      _ <- logInfo("Task sent to execution")
      _ <- sendNewStateToDb(updatedTaskInfo)
      _ <- logInfo("Updated task state sent to database")
      _ <- IO.delay(channel.basicAck(envelope.getDeliveryTag, false))
      _ <- logInfo("Acknowledgment sent to broker")
      _ <- logSuccess("Task processing completed")
    yield ()

    val processingIOHandler = processingIO.handleErrorWith {
      case error: YamlError =>
        logError(s"Deserialization error: $error")
          >> IO.delay(channel.basicNack(envelope.getDeliveryTag, false, true))
      case _ =>
        IO.delay(channel.basicNack(envelope.getDeliveryTag, false, true))
    }

    processingIOHandler.unsafeRunAndForget()
  end handleDelivery

  def processMessage(taskInfo: TaskInfo): IO[TaskInfo] =
    // try to get file from ftp server
    // if internal error, update state to to InternalServerError
    // if file not found, update state to FileNotFound
    // if file found try to deserialize it
    // if deserialization error, update state to DeserializationError
    // if deserialization success, update state to DeserializationSuccess
    // if deserialization success, try to parse the file
    // if parsing error, update state to ParsingError
    // if parsing success, update state to ParsingSuccess
    // return updated taskInfo
    IO.pure(taskInfo)

  def sendToFinzalization(taskInfo: TaskInfo): IO[Unit] =
    IO.delay(publishFunction(successRoutingKey, serializeMessage(taskInfo)))

  def sendNewStateToDb(taskInfo: TaskInfo): IO[Unit] =
    IO.delay(publishFunction(databaseRoutingKey, serializeMessage(taskInfo)))
end ExecutionConsumer

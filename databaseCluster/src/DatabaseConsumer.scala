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
import types.ProcessingConsumer
import types.TaskInfo

import scala.concurrent.duration.*

case class DatabaseConsumer(
    channel: Channel
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
      _ <- logInfo("Message received")
      _ <- logInfo("Attempting to deserialize message...")
      taskInfo <- IO.fromEither(deserializeMessage(body.toSeq))
      _ <- logInfo("Message deserialization successfull")
      _ <- logInfo("Attempting to save task state to database...")
      _ <- saveTaskInfoToDatabase(taskInfo)
      _ <- logInfo("Task state saved to database")
      _ <- logInfo("Attempting to acknowledge message...")
      _ <- IO.delay(channel.basicAck(envelope.getDeliveryTag, false))
      _ <- logInfo("Message acknowledged")
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

  def saveTaskInfoToDatabase(taskInfo: TaskInfo): IO[Unit] =
    // for now we just simulate the database operation
    for _ <- IO.sleep(5.second)
    yield ()

end DatabaseConsumer

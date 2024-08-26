/** @author
  *   Esteban Gonzalez Ruales
  */

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import logging.LoggingUtil.terminalLogError
import logging.LoggingUtil.terminalLogInfo
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

  val classType = classOf[ExecutionConsumer]
  val logInfo = terminalLogInfo(classType)
  val logError = terminalLogError(classType)
  override def handleDelivery(
      consumerTag: String,
      envelope: Envelope,
      properties: BasicProperties,
      body: Array[Byte]
  ): Unit =

    val processingIO = for
      _ <- logInfo(
        s"Message received. ConsumerTag: $consumerTag. DeliveryTag: ${envelope.getDeliveryTag}"
      )
      possibleTask = deserializeMessage(body.toSeq)
      _ <- possibleTask match
        case Left(error) =>
          logError(s"Deserialization error: $error")
            >> IO.delay(channel.basicNack(envelope.getDeliveryTag, false, true))

        case Right(taskInfo) =>
          logInfo(s"Deserialization success")
            >> IO {
              val updatedTaskInfo = processMessage(taskInfo)
              updatedTaskInfo.state match
                case "this will not happen for now" => ()
                case _                              => handleNextStep(taskInfo)
              end match

              sendNewStateToDb(updatedTaskInfo)
            }
            >> logInfo("Updated task state sent to database")
            >> IO.delay(channel.basicAck(envelope.getDeliveryTag, false))
            >> logInfo("Acknowledgment sent to broker")
    yield ()

    processingIO.unsafeRunAndForget()

    // logInfo(
    //   s"Message received. ConsumerTag: $consumerTag. DeliveryTag: ${envelope.getDeliveryTag}"
    // )

    // val possibleTask = deserializeMessage(body.toSeq)

    // possibleTask match
    //   case Left(error) =>
    //     logError(s"Deserialization error: $error")
    //     channel.basicNack(envelope.getDeliveryTag, false, true)
    //   case Right(taskInfo) =>
    //     logInfo(s"Deserialization success")

    //     val updatedTaskInfo = processMessage(taskInfo)
    //     updatedTaskInfo.state match
    //       case "this will not happen for now" => ()
    //       case _                              => handleNextStep(taskInfo)
    //     end match

    //     sendNewStateToDb(updatedTaskInfo)
    //     logInfo("Updated task state sent to database")

    //     channel.basicAck(envelope.getDeliveryTag, false)
    //     logInfo("Acknowledgment sent to broker")
    // end match
  end handleDelivery

  override def processMessage(taskInfo: TaskInfo): TaskInfo =
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
    taskInfo

  def handleNextStep(taskInfo: TaskInfo): Unit =
    publishFunction(successRoutingKey, serializeMessage(taskInfo))

  def handleError(taskInfo: TaskInfo): Unit = ???

  def sendNewStateToDb(taskInfo: TaskInfo): Unit =
    publishFunction(databaseRoutingKey, serializeMessage(taskInfo))
end ExecutionConsumer

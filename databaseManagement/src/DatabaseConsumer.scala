import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import types.ProcessingConsumer
import types.StateTypes.*
import types.TaskInfo

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

    val possibleTask = deserializeMessage(body.toSeq)

    possibleTask match
      case Left(error) =>
        println(s" [x] Error deserializing message: $error")
        channel.basicNack(envelope.getDeliveryTag, false, true)
      case Right(taskInfo) =>
        println(s" [x] Saving to database")
        val _ = processMessage(taskInfo).state
        channel.basicAck(envelope.getDeliveryTag, false)

  def processMessage(taskInfo: TaskInfo): TaskInfo =
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

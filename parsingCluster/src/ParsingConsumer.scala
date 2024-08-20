import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import types.OpaqueTypes.RoutingKey
import types.ProcessingConsumer
import types.StateTypes.*
import types.TaskInfo

case class ParsingConsumer(
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
    // println(s" [x] Received '$message' by consumer with tag $consumerTag")
    // println(s" [x] Acknowledged message with tag ${envelope.getDeliveryTag}")

    val possibleTask = deserializeMessage(body.toSeq)

    possibleTask match
      case Left(error) =>
        println(s" [x] Error deserializing message: $error")
        channel.basicNack(envelope.getDeliveryTag, false, true)
      case Right(taskInfo) =>
        println(s" [x] Parsing message")
        processMessage(taskInfo).state match
          case "this will not happen for now" => ()
          case _                              => handleNextStep(taskInfo)
        println(s" [x] Sending new state to database")
        sendNewStateToDb(taskInfo)
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

  def handleNextStep(taskInfo: TaskInfo): Unit =
    publishFunction(successRoutingKey, serializeMessage(taskInfo))

  def handleError(taskInfo: TaskInfo): Unit = ???

  def sendNewStateToDb(taskInfo: TaskInfo): Unit =
    publishFunction(databaseRoutingKey, serializeMessage(taskInfo))

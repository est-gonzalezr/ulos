package types

import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.Envelope

/** This class represents a message that can either be received or sent to the
  * Message Queue.
  *
  * @param id
  *   The unique identifier of the message
  * @param bytes
  *   The bytes of the message
  */
final case class MqMessage(
    consumerTag: String,
    envelope: Envelope,
    properties: BasicProperties,
    body: Seq[Byte]
):
end MqMessage

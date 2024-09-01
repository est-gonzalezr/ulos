/** @author
  *   Esteban Gonzalez Ruales
  */

package types

import cats.effect.IO
import com.rabbitmq.client.DefaultConsumer
import logging.LoggingUtil.terminalLogError
import logging.LoggingUtil.terminalLogInfo
import logging.LoggingUtil.terminalLogSuccess
import org.virtuslab.yaml.AnyOps
import org.virtuslab.yaml.StringOps
import org.virtuslab.yaml.YamlError

/** Represents a consumer that is part of the prcessing lifecycle of a task.
  */
trait ProcessingConsumer:
  this: DefaultConsumer =>

  /** Decodes a message from a sequence of bytes to a TaskInfo object.
    *
    * @param message
    *   The message to decode
    *
    * @return
    *   The TaskInfo object
    */
  def deserializeMessage(
      message: Seq[Byte]
  ): Either[YamlError, TaskInfo] =
    message
      .map(_.toChar)
      .mkString
      .as[TaskInfo]

  /** Encodes a TaskInfo object to a sequence of bytes.
    *
    * @param taskInfo
    *   The TaskInfo object to encode
    *
    * @return
    *   The sequence of bytes
    */
  def serializeMessage(taskInfo: TaskInfo): Seq[Byte] =
    taskInfo.asYaml.getBytes.toSeq

  /** Creates a message with the consumer tag and the delivery tag.
    *
    * @param deliveryTag
    *   The delivery tag
    * @param message
    *   The message
    *
    * @return
    *   The message with the consumer tag and the delivery tag
    */
  private def messageWithConsumerTagAndDeliveryTag(
      consumerTag: String,
      deliveryTag: Long,
      message: String
  ): String =
    s"{$consumerTag} {$deliveryTag} $message"

  /** Logs an info message with the consumer tag and the delivery tag.
    *
    * @param consumerTag
    *   The consumer tag
    * @param deliveryTag
    *   The delivery tag
    * @param message
    *   The message
    *
    * @return
    *   An IO action that logs the message
    */
  def logConsumerInfo(consumerTag: String, deliveryTag: Long)(
      message: String
  ): IO[Unit] =
    terminalLogInfo(this.getClass)(
      messageWithConsumerTagAndDeliveryTag(consumerTag, deliveryTag, message)
    )

  /** Logs an error message with the consumer tag and the delivery tag.
    *
    * @param consumerTag
    *   The consumer tag
    * @param deliveryTag
    *   The delivery tag
    * @param message
    *   The message
    *
    * @return
    *   An IO action that logs the message
    */
  def logConsumerError(consumerTag: String, deliveryTag: Long)(
      message: String
  ): IO[Unit] =
    terminalLogError(this.getClass)(
      messageWithConsumerTagAndDeliveryTag(consumerTag, deliveryTag, message)
    )

  /** Logs a success message with the consumer tag and the delivery tag.
    *
    * @param consumerTag
    *   The consumer tag
    * @param deliveryTag
    *   The delivery tag
    * @param message
    *   The message
    *
    * @return
    *   An IO action that logs the message
    */
  def logConsumerSuccess(consumerTag: String, deliveryTag: Long)(
      message: String
  ): IO[Unit] =
    terminalLogSuccess(this.getClass)(
      messageWithConsumerTagAndDeliveryTag(consumerTag, deliveryTag, message)
    )
end ProcessingConsumer

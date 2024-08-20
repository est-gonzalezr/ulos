/** @author
  *   Esteban Gonzalez Ruales
  */

package types

import org.virtuslab.yaml.AnyOps
import org.virtuslab.yaml.StringOps
import org.virtuslab.yaml.YamlError

/** Represents a consumer that is part of the prcessing lifecycle of a task.
  */
trait ProcessingConsumer:

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

  /** Processes a message.
    *
    * @param taskInfo
    *   The TaskInfo object to process
    *
    * @return
    *   The TaskInfo object after processing
    */
  def processMessage(taskInfo: TaskInfo): TaskInfo

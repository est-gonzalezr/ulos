package types

/** @author
  *   Esteban Gonzalez Ruales
  */

/** This class represents a message that can either be received or sent to the
  * Message Queue.
  *
  * @param id
  *   The unique identifier of the message
  * @param bytes
  *   The bytes of the message
  */
final case class MqMessage(id: Long, bytes: Seq[Byte])

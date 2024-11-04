/** @author
  *   Esteban Gonzalez Ruales
  */

package types

/** Provides the opaque types for the messaging system elements. This is a way
  * to provide a type-safe way to handle the different elements of the messaging
  * system without exposing the actual type of the elements. This makes the code
  * more robust and less error-prone.
  */
object OpaqueTypes:

  /** Represents the routing key for the messaging system.
    */
  opaque type RoutingKey = String
  object RoutingKey:
    /** Creates a new RoutingKey.
      *
      * @param value
      *   The value to use
      *
      * @return
      *   A new RoutingKey
      */
    def apply(value: String): RoutingKey = value

    /** Returns the value of the RoutingKey.
      *
      * @param routingKey
      *   The RoutingKey
      *
      * @return
      *   The value
      */
    extension (routingKey: RoutingKey) def value: String = routingKey
  end RoutingKey

  /** Represents the exchange name for the messaging system.
    */
  opaque type ExchangeName = String
  object ExchangeName:
    /** Creates a new ExchangeName.
      *
      * @param value
      *   The value to use
      *
      * @return
      *   A new ExchangeName
      */
    def apply(value: String): ExchangeName = value

    /** Returns the value of the ExchangeName.
      *
      * @param routingKey
      *   The ExchangeName
      *
      * @return
      *   The value
      */
    extension (routingKey: ExchangeName) def value: String = routingKey
  end ExchangeName

  /** Represents the queue name for the messaging system.
    */
  opaque type QueueName = String
  object QueueName:
    /** Creates a new QueueName.
      *
      * @param value
      *   The value to use
      *
      * @return
      *   A new QueueName
      */
    def apply(value: String): QueueName = value

    /** Returns the value of the QueueName.
      *
      * @param queueName
      *   The QueueName
      *
      * @return
      *   The value
      */
    extension (queueName: QueueName) def value: String = queueName
  end QueueName

  /** Represents the message id for the messaging system.
    */
  opaque type MqMessageId = String
  object MqMessageId:
    /** Creates a new MqMessageId.
      *
      * @param value
      *   The value to use
      *
      * @return
      */
    def apply(value: String): MqMessageId = value

    /** Returns the value of the MqMessageId.
      *
      * @param mqMessageId
      *   The MqMessageId
      *
      * @return
      *   The value
      */
    extension (mqMessageId: MqMessageId) def value: String = mqMessageId
  end MqMessageId

  /** Represents the task id for the messaging system.
    */
  opaque type Uri = String
  object Uri:
    /** Creates a new Uri.
      *
      * @param value
      *   The value to use
      *
      * @return
      *   A new Uri
      */
    def apply(value: String): Uri = value

    /** Returns the value of the Uri.
      *
      * @param uri
      *   The Uri
      *
      * @return
      *   The value
      */
    extension (uri: Uri) def value: String = uri
  end Uri

  /** Represents the local path for the messaging system.
    */
  opaque type LocalPath = String
  object LocalPath:

    /** Creates a new LocalPath.
      *
      * @param value
      *   The value to use
      *
      * @return
      *   A new LocalPath
      */
    def apply(value: String): LocalPath = value

    /** Returns the value of the LocalPath.
      *
      * @param localPath
      *   The LocalPath
      *
      * @return
      *   The value
      */
    extension (localPath: LocalPath) def value: String = localPath
  end LocalPath
end OpaqueTypes

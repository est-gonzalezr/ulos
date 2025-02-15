package types

/** @author
  *   Esteban Gonzalez Ruales
  */

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

  /** Represents the task uri for the messaging system.
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

  /** Represents the host for the messaging system.
    */
  opaque type MqHost = String
  object MqHost:
    /** Creates a new MqHost.
      *
      * @param value
      *   The value to use
      * @return
      *   A new MqHost
      */
    def apply(value: String): MqHost = value

    /** Returns the value of the MqHost.
      *
      * @param mqHost
      *   The MqHost
      * @return
      *   The value
      */
    extension (mqHost: MqHost) def value: String = mqHost
  end MqHost

  /** Represents the port for the messaging system.
    */
  opaque type MqPort = Int
  object MqPort:
    /** Creates a new MqPort.
      *
      * @param value
      *   The value to use
      * @return
      *   A new MqPort
      */
    def apply(value: Int): MqPort = value

    /** Returns the value of the MqPort.
      *
      * @param mqPort
      *   The MqPort
      * @return
      *   The value
      */
    extension (mqPort: MqPort) def value: Int = mqPort
  end MqPort

  /** Represents the user for the messaging system.
    */
  opaque type MqUser = String
  object MqUser:
    /** Creates a new MqUser.
      *
      * @param value
      *   The value to use
      * @return
      *   A new MqUser
      */
    def apply(value: String): MqUser = value

    /** Returns the value of the MqUser.
      *
      * @param mqUser
      *   The MqUser
      * @return
      *   The value
      */
    extension (mqUser: MqUser) def value: String = mqUser
  end MqUser

  /** Represents the password for the messaging system.
    */
  opaque type MqPassword = String
  object MqPassword:
    /** Creates a new MqPassword.
      *
      * @param value
      *   The value to use
      * @return
      *   A new MqPassword
      */
    def apply(value: String): MqPassword = value

    /** Returns the value of the MqPassword.
      *
      * @param mqPassword
      *   The MqPassword
      * @return
      *   The value
      */
    extension (mqPassword: MqPassword) def value: String = mqPassword
  end MqPassword

  /** Represents the remote storage host for the messaging system.
    */
  opaque type RemoteStorageHost = String
  object RemoteStorageHost:
    /** Creates a new RemoteStorageHost.
      *
      * @param value
      *   The value to use
      * @return
      *   A new RemoteStorageHost
      */
    def apply(value: String): RemoteStorageHost = value

    /** Returns the value of the RemoteStorageHost.
      *
      * @param remoteStorageHost
      *   The RemoteStorageHost
      * @return
      *   The value
      */
    extension (remoteStorageHost: RemoteStorageHost)
      def value: String =
        remoteStorageHost
  end RemoteStorageHost

  /** Represents the remote storage port for the messaging system.
    */
  opaque type RemoteStoragePort = Int
  object RemoteStoragePort:
    /** Creates a new RemoteStoragePort.
      *
      * @param value
      *   The value to use
      * @return
      *   A new RemoteStoragePort
      */
    def apply(value: Int): RemoteStoragePort = value

    /** Returns the value of the RemoteStoragePort.
      *
      * @param remoteStoragePort
      *   The RemoteStoragePort
      * @return
      *   The value
      */
    extension (remoteStoragePort: RemoteStoragePort)
      def value: Int =
        remoteStoragePort
  end RemoteStoragePort

  /** Represents the remote storage user for the messaging system.
    */
  opaque type RemoteStorageUser = String
  object RemoteStorageUser:
    /** Creates a new RemoteStorageUser.
      *
      * @param value
      *   The value to use
      * @return
      *   A new RemoteStorageUser
      */
    def apply(value: String): RemoteStorageUser = value

    /** Returns the value of the RemoteStorageUser.
      *
      * @param remoteStorageUser
      *   The RemoteStorageUser
      * @return
      *   The value
      */
    extension (remoteStorageUser: RemoteStorageUser)
      def value: String =
        remoteStorageUser
  end RemoteStorageUser

  /** Represents the remote storage password for the messaging system.
    */
  opaque type RemoteStoragePassword = String
  object RemoteStoragePassword:
    /** Creates a new RemoteStoragePassword.
      *
      * @param value
      *   The value to use
      * @return
      *   A new RemoteStoragePassword
      */
    def apply(value: String): RemoteStoragePassword = value

    /** Returns the value of the RemoteStoragePassword.
      *
      * @param remoteStoragePassword
      *   The RemoteStoragePassword
      * @return
      *   The value
      */
    extension (remoteStoragePassword: RemoteStoragePassword)
      def value: String =
        remoteStoragePassword
  end RemoteStoragePassword
end OpaqueTypes

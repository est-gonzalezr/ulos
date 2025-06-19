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
    end extension
  end RoutingKey

  /** Represents the exchange name for the messaging system.
    */
  opaque type MqExchangeName = String
  object MqExchangeName:
    /** Creates a new MqExchangeName.
      *
      * @param value
      *   The value to use
      *
      * @return
      *   A new MqExchangeName
      */
    def apply(value: String): MqExchangeName = value

    /** Returns the value of the MqExchangeName.
      *
      * @param routingKey
      *   The MqExchangeName
      *
      * @return
      *   The value
      */
    extension (routingKey: MqExchangeName) def value: String = routingKey
    end extension
  end MqExchangeName

  /** Represents the queue name for the messaging system.
    */
  opaque type MqQueueName = String
  object MqQueueName:
    /** Creates a new MqQueueName.
      *
      * @param value
      *   The value to use
      *
      * @return
      *   A new MqQueueName
      */
    def apply(value: String): MqQueueName = value

    /** Returns the value of the MqQueueName.
      *
      * @param queueName
      *   The MqQueueName
      *
      * @return
      *   The value
      */
    extension (queueName: MqQueueName) def value: String = queueName
    end extension
  end MqQueueName

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
    end extension
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
    end extension
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
    end extension
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
    end extension
  end MqPort

  /** Represents the user for the messaging system.
    */
  opaque type MqUsername = String
  object MqUsername:
    /** Creates a new MqUser.
      *
      * @param value
      *   The value to use
      * @return
      *   A new MqUser
      */
    def apply(value: String): MqUsername = value

    /** Returns the value of the MqUser.
      *
      * @param mqUser
      *   The MqUser
      * @return
      *   The value
      */
    extension (mqUser: MqUsername) def value: String = mqUser
    end extension
  end MqUsername

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
    end extension
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
    end extension
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
    end extension
  end RemoteStoragePort

  /** Represents the remote storage user for the messaging system.
    */
  opaque type RemoteStorageUsername = String
  object RemoteStorageUsername:
    /** Creates a new RemoteStorageUser.
      *
      * @param value
      *   The value to use
      * @return
      *   A new RemoteStorageUser
      */
    def apply(value: String): RemoteStorageUsername = value

    /** Returns the value of the RemoteStorageUser.
      *
      * @param remoteStorageUser
      *   The RemoteStorageUser
      * @return
      *   The value
      */
    extension (remoteStorageUser: RemoteStorageUsername)
      def value: String =
        remoteStorageUser
    end extension
  end RemoteStorageUsername

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
    end extension
  end RemoteStoragePassword
end OpaqueTypes

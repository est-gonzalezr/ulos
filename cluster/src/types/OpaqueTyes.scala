package types

/** Provides the opaque types for the messaging system elements. This is a way
  * to provide a type-safe way to handle the different elements of the messaging
  * system without exposing the actual type of the elements. This makes the code
  * more robust and less error-prone.
  */
object OpaqueTypes:

  opaque type MessageBrokerRoutingKey = String
  object MessageBrokerRoutingKey:
    def apply(value: String): MessageBrokerRoutingKey = value
    extension (routingKey: MessageBrokerRoutingKey)
      def value: String = routingKey
    end extension
  end MessageBrokerRoutingKey

  opaque type MessageBrokerExchangeName = String
  object MessageBrokerExchangeName:
    def apply(value: String): MessageBrokerExchangeName = value
    extension (routingKey: MessageBrokerExchangeName)
      def value: String = routingKey
    end extension
  end MessageBrokerExchangeName

  opaque type MessageBrokerQueueName = String
  object MessageBrokerQueueName:
    def apply(value: String): MessageBrokerQueueName = value
    extension (queueName: MessageBrokerQueueName) def value: String = queueName
    end extension
  end MessageBrokerQueueName

  opaque type MessageBrokerHost = String
  object MessageBrokerHost:
    def apply(value: String): MessageBrokerHost = value
    extension (mqHost: MessageBrokerHost) def value: String = mqHost
    end extension
  end MessageBrokerHost

  opaque type MessageBrokerPort = Int
  object MessageBrokerPort:
    def apply(value: Int): MessageBrokerPort = value
    extension (mqPort: MessageBrokerPort) def value: Int = mqPort
    end extension
  end MessageBrokerPort

  opaque type MessageBrokerUsername = String
  object MessageBrokerUsername:
    def apply(value: String): MessageBrokerUsername = value
    extension (mqUser: MessageBrokerUsername) def value: String = mqUser
    end extension
  end MessageBrokerUsername

  opaque type MessageBrokerPassword = String
  object MessageBrokerPassword:
    def apply(value: String): MessageBrokerPassword = value
    extension (mqPassword: MessageBrokerPassword) def value: String = mqPassword
    end extension
  end MessageBrokerPassword

  opaque type RemoteStorageHost = String
  object RemoteStorageHost:
    def apply(value: String): RemoteStorageHost = value
    extension (remoteStorageHost: RemoteStorageHost)
      def value: String = remoteStorageHost
    end extension
  end RemoteStorageHost

  opaque type RemoteStoragePort = Int
  object RemoteStoragePort:
    def apply(value: Int): RemoteStoragePort = value
    extension (remoteStoragePort: RemoteStoragePort)
      def value: Int = remoteStoragePort
    end extension
  end RemoteStoragePort

  opaque type RemoteStorageUsername = String
  object RemoteStorageUsername:
    def apply(value: String): RemoteStorageUsername = value
    extension (remoteStorageUser: RemoteStorageUsername)
      def value: String = remoteStorageUser
    end extension
  end RemoteStorageUsername

  opaque type RemoteStoragePassword = String
  object RemoteStoragePassword:
    def apply(value: String): RemoteStoragePassword = value
    extension (remoteStoragePassword: RemoteStoragePassword)
      def value: String = remoteStoragePassword
    end extension
  end RemoteStoragePassword
end OpaqueTypes

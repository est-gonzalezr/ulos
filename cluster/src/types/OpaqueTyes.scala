package types

import pureconfig.ConfigReader
import zio.json.JsonDecoder
import zio.json.JsonEncoder

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

    given ConfigReader[MessageBrokerRoutingKey] =
      ConfigReader.fromString(str => Right(MessageBrokerRoutingKey(str)))
    given JsonDecoder[MessageBrokerRoutingKey] =
      JsonDecoder[String].map(MessageBrokerRoutingKey(_))
    given JsonEncoder[MessageBrokerRoutingKey] =
      JsonEncoder[String].contramap(_.value)
  end MessageBrokerRoutingKey

  opaque type MessageBrokerExchange = String
  object MessageBrokerExchange:
    def apply(value: String): MessageBrokerExchange = value
    extension (routingKey: MessageBrokerExchange) def value: String = routingKey
    end extension

    given ConfigReader[MessageBrokerExchange] =
      ConfigReader.fromString(str => Right(MessageBrokerExchange(str)))
    given JsonDecoder[MessageBrokerExchange] =
      JsonDecoder[String].map(MessageBrokerExchange(_))
    given JsonEncoder[MessageBrokerExchange] =
      JsonEncoder[String].contramap(_.value)
  end MessageBrokerExchange

  opaque type MessageBrokerQueue = String
  object MessageBrokerQueue:
    def apply(value: String): MessageBrokerQueue = value
    extension (queueName: MessageBrokerQueue) def value: String = queueName
    end extension

    given ConfigReader[MessageBrokerQueue] =
      ConfigReader.fromString(str => Right(MessageBrokerQueue(str)))
  end MessageBrokerQueue

  opaque type MessageBrokerHost = String
  object MessageBrokerHost:
    def apply(value: String): MessageBrokerHost = value
    extension (mqHost: MessageBrokerHost) def value: String = mqHost
    end extension

    given ConfigReader[MessageBrokerHost] =
      ConfigReader.fromString(str => Right(MessageBrokerHost(str)))
  end MessageBrokerHost

  opaque type MessageBrokerPort = Int
  object MessageBrokerPort:
    def apply(value: Int): MessageBrokerPort = value
    extension (mqPort: MessageBrokerPort) def value: Int = mqPort
    end extension

    given ConfigReader[MessageBrokerPort] =
      ConfigReader.fromCursor(_.asInt.map(MessageBrokerPort(_)))
  end MessageBrokerPort

  opaque type MessageBrokerUsername = String
  object MessageBrokerUsername:
    def apply(value: String): MessageBrokerUsername = value
    extension (mqUser: MessageBrokerUsername) def value: String = mqUser
    end extension

    given ConfigReader[MessageBrokerUsername] =
      ConfigReader.fromString(str => Right(MessageBrokerUsername(str)))
  end MessageBrokerUsername

  opaque type MessageBrokerPassword = String
  object MessageBrokerPassword:
    def apply(value: String): MessageBrokerPassword = value
    extension (mqPassword: MessageBrokerPassword) def value: String = mqPassword
    end extension

    given ConfigReader[MessageBrokerPassword] =
      ConfigReader.fromString(str => Right(MessageBrokerPassword(str)))
  end MessageBrokerPassword

  opaque type RemoteStorageHost = String
  object RemoteStorageHost:
    def apply(value: String): RemoteStorageHost = value
    extension (remoteStorageHost: RemoteStorageHost)
      def value: String = remoteStorageHost
    end extension

    given ConfigReader[RemoteStorageHost] =
      ConfigReader.fromString(str => Right(RemoteStorageHost(str)))
  end RemoteStorageHost

  opaque type RemoteStoragePort = Int
  object RemoteStoragePort:
    def apply(value: Int): RemoteStoragePort = value
    extension (remoteStoragePort: RemoteStoragePort)
      def value: Int = remoteStoragePort
    end extension

    given ConfigReader[RemoteStoragePort] =
      ConfigReader.fromCursor(_.asInt.map(RemoteStoragePort(_)))
  end RemoteStoragePort

  opaque type RemoteStorageUsername = String
  object RemoteStorageUsername:
    def apply(value: String): RemoteStorageUsername = value
    extension (remoteStorageUser: RemoteStorageUsername)
      def value: String = remoteStorageUser
    end extension

    given ConfigReader[RemoteStorageUsername] =
      ConfigReader.fromString(str => Right(RemoteStorageUsername(str)))
  end RemoteStorageUsername

  opaque type RemoteStoragePassword = String
  object RemoteStoragePassword:
    def apply(value: String): RemoteStoragePassword = value
    extension (remoteStoragePassword: RemoteStoragePassword)
      def value: String = remoteStoragePassword
    end extension

    given ConfigReader[RemoteStoragePassword] =
      ConfigReader.fromString(str => Right(RemoteStoragePassword(str)))
  end RemoteStoragePassword
end OpaqueTypes

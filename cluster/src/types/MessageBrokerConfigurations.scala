package types

import pureconfig.ConfigReader
import types.OpaqueTypes.MessageBrokerHost
import types.OpaqueTypes.MessageBrokerPassword
import types.OpaqueTypes.MessageBrokerPort
import types.OpaqueTypes.MessageBrokerUsername

/** Connection parameters for the message queue.
  */
final case class MessageBrokerConfigurations(
    host: MessageBrokerHost,
    port: MessageBrokerPort,
    username: MessageBrokerUsername,
    password: MessageBrokerPassword,
    prefetchCount: Int,
    requeueOnReject: Boolean
) derives ConfigReader

object MessageBrokerConfigurations:
  given ConfigReader[MessageBrokerHost] =
    ConfigReader.fromString(str => Right(MessageBrokerHost(str)))

  given ConfigReader[MessageBrokerPort] =
    ConfigReader.fromCursor(_.asInt.map(MessageBrokerPort(_)))

  given ConfigReader[MessageBrokerUsername] =
    ConfigReader.fromString(str => Right(MessageBrokerUsername(str)))

  given ConfigReader[MessageBrokerPassword] =
    ConfigReader.fromString(str => Right(MessageBrokerPassword(str)))

end MessageBrokerConfigurations

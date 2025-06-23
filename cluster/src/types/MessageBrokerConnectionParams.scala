package types

import types.OpaqueTypes.MessageBrokerHost
import types.OpaqueTypes.MessageBrokerPassword
import types.OpaqueTypes.MessageBrokerPort
import types.OpaqueTypes.MessageBrokerUsername

/** Connection parameters for the message queue.
  */
final case class MessageBrokerConnectionParams(
    host: MessageBrokerHost,
    port: MessageBrokerPort,
    username: MessageBrokerUsername,
    password: MessageBrokerPassword
)

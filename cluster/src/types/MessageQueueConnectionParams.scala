package types

import types.OpaqueTypes.MessageBrokerHost
import types.OpaqueTypes.MessageBrokerPort
import types.OpaqueTypes.MessageBrokerUsername
import types.OpaqueTypes.MessageBrokerPassword

/** Connection parameters for the message queue.
  */
final case class MessageQueueConnectionParams(
    host: MessageBrokerHost,
    port: MessageBrokerPort,
    username: MessageBrokerUsername,
    password: MessageBrokerPassword
)

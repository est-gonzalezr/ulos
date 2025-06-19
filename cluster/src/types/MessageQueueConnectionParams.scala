package types

import types.OpaqueTypes.MqHost
import types.OpaqueTypes.MqPassword
import types.OpaqueTypes.MqPort
import types.OpaqueTypes.MqUsername

/** Connection parameters for the message queue.
  */
final case class MessageQueueConnectionParams(
    host: MqHost,
    port: MqPort,
    username: MqUsername,
    password: MqPassword
)

package types

import pureconfig.ConfigReader
import types.OpaqueTypes.*

final case class AppConfig(
    remoteStorageConfig: RemoteStorageConnectionParams,
    messageBrokerConfig: MessageBrokerConnectionParams,
    messageBrokerLogsExchange: MessageBrokerExchange,
    messageBrokerLogsRoutingKey: MessageBrokerRoutingKey,
    messageBrokerCrashExchange: MessageBrokerExchange,
    messageBrokerCrashRoutingKey: MessageBrokerRoutingKey,
    messageBrokerConsumptionQueue: MessageBrokerQueue,
    messageBrokerPrefetchCount: Int
) derives ConfigReader:
  require(messageBrokerPrefetchCount >= 0)
end AppConfig

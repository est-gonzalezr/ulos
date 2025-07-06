package types

import pureconfig.ConfigReader
import types.OpaqueTypes.*

final case class AppConfig(
    remoteStorageConfig: RemoteStorageConfiguration,
    messageBrokerConfig: MessageBrokerConfigurations,
    messageBrokerLogsExchange: MessageBrokerExchange,
    messageBrokerLogsRoutingKey: MessageBrokerRoutingKey,
    messageBrokerCrashExchange: MessageBrokerExchange,
    messageBrokerCrashRoutingKey: MessageBrokerRoutingKey,
    messageBrokerConsumptionQueue: MessageBrokerQueue
) derives ConfigReader:
  require(
    messageBrokerConfig.prefetchCount >= 0,
    "Message broker prefetch count must be non-negative"
  )
end AppConfig

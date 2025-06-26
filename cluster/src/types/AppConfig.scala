package types

import pureconfig.ConfigReader
import types.OpaqueTypes.*

final case class AppConfig(
    messageBrokerConfig: MessageBrokerConnectionParams,
    remoteStorageConfig: RemoteStorageConnectionParams,
    logsExchange: MessageBrokerExchange,
    logsRoutingKey: MessageBrokerRoutingKey,
    consumptionQueue: MessageBrokerQueue
) derives ConfigReader

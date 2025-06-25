package types

import pureconfig.ConfigReader
import types.OpaqueTypes.*

final case class AppConfig(
    messageBrokerConfig: MessageBrokerConnectionParams,
    remoteStorageConfig: RemoteStorageConnectionParams,
    logsExchange: MessageBrokerExchange,
    consumptionQueue: MessageBrokerQueue
) derives ConfigReader

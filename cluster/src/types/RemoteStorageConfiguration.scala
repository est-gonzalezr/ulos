package types

import pureconfig.ConfigReader
import types.OpaqueTypes.RemoteStorageHost
import types.OpaqueTypes.RemoteStoragePassword
import types.OpaqueTypes.RemoteStoragePort
import types.OpaqueTypes.RemoteStorageUsername

/** Connection parameters for the remote storage.
  */
final case class RemoteStorageConfiguration(
    host: RemoteStorageHost,
    port: RemoteStoragePort,
    username: RemoteStorageUsername,
    password: RemoteStoragePassword
) derives ConfigReader

package types

import actors.SystemMonitor
import actors.execution.ExecutionManager
import actors.mq.MessageBrokerManager
import actors.storage.RemoteStorageManager
import org.apache.pekko.actor.typed.ActorRef

/** Represents the setup of the Orchestrator.
  * @param mqManager
  *   The message queue manager actor reference.
  * @param storageManager
  *   The remote storage manager actor reference.
  * @param executionManager
  *   The execution manager actor reference.
  * @param systemMonitor
  *   The system monitor actor reference.
  */
final case class OrchestratorSetup(
    messageQueueManager: ActorRef[MessageBrokerManager.Command],
    remoteStorageManager: ActorRef[RemoteStorageManager.Command],
    executionManager: ActorRef[ExecutionManager.Command],
    systemMonitor: ActorRef[SystemMonitor.Command]
)

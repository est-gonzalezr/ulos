/**
 * @author
 *   Esteban Gonzalez Ruales
 */

import actors.Orchestrator
import akka.actor.typed.ActorSystem
import types.OpaqueTypes.*

@main def main(): Unit =
  val mqHost = MqHost("localhost")
  val mqPort = MqPort(5672)
  val mqUser = MqUser("guest")
  val mqPassword = MqPassword("guest")
  val mqExchangeName = ExchangeName("processing-exchange")
  val mqQueueName = QueueName("processing-queue")
  val remoteStorageHost = RemoteStorageHost("localhost")
  val remoteStoragePort = RemoteStoragePort(21)
  val remoteStorageUser = RemoteStorageUser("one")
  val remoteStoragePassword = RemoteStoragePassword("123")

  val _ = ActorSystem(
    Orchestrator(
      mqHost,
      mqPort,
      mqUser,
      mqPassword,
      mqExchangeName,
      mqQueueName,
      remoteStorageHost,
      remoteStoragePort,
      remoteStorageUser,
      remoteStoragePassword,
    ),
    "task-orchestrator",
  )
  // guardian.terminate()

  // val path = Path("/Users/estebangonzalezruales/Downloads/ulos/ftp/one/task1")
  // println(
  //   os.zip(
  //     Path("/Users/estebangonzalezruales/Downloads/ulos/ftp/one/task1.zip"),
  //     Seq(Path("/Users/estebangonzalezruales/Downloads/ulos/ftp/one/task1"))
  //   )
  // )
  // val _ = os.remove.all(path)
  println("Cluster application started successfully!")
end main

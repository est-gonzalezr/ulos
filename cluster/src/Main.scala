import scala.sys

import actors.Orchestrator
import akka.actor.typed.ActorSystem
import types.MessageQueueConnectionParams
import types.OpaqueTypes.*
import types.RemoteStorageConnectionParams

@main def main(): Unit =
  getEnvVars() match
    case Right(envMap) =>
      val mqConnParams = MessageQueueConnectionParams(
        MessageBrokerHost(envMap("MQ_HOST")),
        MessageBrokerPort(envMap("MQ_PORT").toInt),
        MessageBrokerUsername(envMap("MQ_USER")),
        MessageBrokerPassword(envMap("MQ_PASSWORD"))
      )

      val rsConnParams = RemoteStorageConnectionParams(
        RemoteStorageHost(envMap("REMOTE_STORAGE_HOST")),
        RemoteStoragePort(envMap("REMOTE_STORAGE_PORT").toInt),
        RemoteStorageUsername(envMap("REMOTE_STORAGE_USER")),
        RemoteStoragePassword(envMap("REMOTE_STORAGE_PASSWORD"))
      )

      val _ = ActorSystem(
        Orchestrator(
          MessageBrokerExchangeName(envMap("MQ_EXCHANGE_NAME")),
          MessageBrokerQueueName(envMap("MQ_QUEUE_NAME")),
          mqConnParams,
          rsConnParams
        ),
        "task-orchestrator"
      )
    case Left(error, errorVars) =>
      println(s"An error was encountered reading environment variales:")
      println(s"$error: $errorVars")
  end match
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

def getEnvVars(): Either[(String, List[String]), Map[String, String]] =
  val requiredEnvVars = List(
    "MQ_HOST",
    "MQ_PORT",
    "MQ_USER",
    "MQ_PASSWORD",
    "MQ_EXCHANGE_NAME",
    "MQ_QUEUE_NAME",
    "REMOTE_STORAGE_HOST",
    "REMOTE_STORAGE_PORT",
    "REMOTE_STORAGE_USER",
    "REMOTE_STORAGE_PASSWORD"
  )

  val intEnvVars = List("MQ_PORT", "REMOTE_STORAGE_PORT")

  val missingEnvVars = requiredEnvVars.filterNot(sys.env.contains)
  val areRequiredInt =
    intEnvVars.filterNot(sys.env.get(_).exists(_.toIntOption.isDefined))

  if missingEnvVars.nonEmpty then Left("Missing env vars", missingEnvVars)
  else if areRequiredInt.nonEmpty then
    Left("Env vars are not INT", areRequiredInt)
  else
    val envMap = requiredEnvVars.map(key => key -> sys.env(key)).toMap
    Right(envMap)
  end if
end getEnvVars

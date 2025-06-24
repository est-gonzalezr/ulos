package src

import actors.Orchestrator
import org.apache.pekko.actor.typed.ActorSystem
import types.MessageBrokerConnectionParams
import types.OpaqueTypes.*
import types.RemoteStorageConnectionParams

import scala.sys

@main def main(): Unit =
  val _ = setup() match
    case Right(createGuardian) =>
      val _ = createGuardian()

    case Left(error) =>
      s"An error was encountered reading environment variables: $error"
end main

def setup(): Either[String, () => ActorSystem[?]] =
  getEnvVars() match
    case Right(envMap) =>
      val mqConnParams = MessageBrokerConnectionParams(
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

      lazy val guardian = ActorSystem(
        Orchestrator(
          MessageBrokerExchange(envMap("MQ_LOGS_EXCHANGE_NAME")),
          MessageBrokerQueue(envMap("MQ_CONSUMPTION_QUEUE_NAME")),
          mqConnParams,
          rsConnParams
        ),
        "task-orchestrator"
      )

      Right(() => guardian)
    case Left(error, errorVars) =>
      println(s"An error was encountered reading environment variales:")
      Left(s"$error: $errorVars")
  end match
end setup

def getEnvVars(): Either[(String, List[String]), Map[String, String]] =
  val requiredEnvVars = List(
    "MQ_HOST",
    "MQ_PORT",
    "MQ_USER",
    "MQ_PASSWORD",
    "MQ_LOGS_EXCHANGE_NAME",
    "MQ_CONSUMPTION_QUEUE_NAME",
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

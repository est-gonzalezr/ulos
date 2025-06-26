package src

import actors.Orchestrator
import org.apache.pekko.actor.typed.ActorSystem
import pureconfig.ConfigSource
import types.AppConfig

@main def main(): Unit =
  val _ = ConfigSource.default.load[AppConfig] match
    case Right(config) => ActorSystem(Orchestrator(config), "orchestrator")
    case Left(error)   =>
      println(s"Error loading configuration: $error")
end main

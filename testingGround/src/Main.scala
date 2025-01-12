import akka.actor.typed.ActorSystem
import actors.Orchestrator

@main def main(): Unit =
  println("Hello, world!")

  val guardian = ActorSystem(Orchestrator(), "task-orchestrator")
  // guardian.terminate()
end main

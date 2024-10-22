import akka.actor.typed.ActorSystem

@main def main(): Unit =
  println("Hello, world!")

  val guardian = ActorSystem(Orchestrator(), "task-orchestrator")
  // guardian.terminate()
end main

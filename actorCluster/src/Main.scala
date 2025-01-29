import actors.Orchestrator
import akka.actor.typed.ActorSystem
import os.RelPath
import os.Path

@main def main(): Unit =

  val _ = ActorSystem(Orchestrator(), "task-orchestrator")
  // guardian.terminate()

end main

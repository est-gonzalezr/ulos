import actors.Orchestrator
import akka.actor.typed.ActorSystem
// import org.apache.pekko.actor.typed.ActorSystem

@main def main(): Unit =

  val _ = ActorSystem(Orchestrator(), "task-orchestrator")
  // guardian.terminate()

end main

import akka.actor.typed.ActorSystem
import actors.Orchestrator
import os.Path
import os.RelPath

@main def main(): Unit =
  println("Hello, world!")
  val path = RelPath("test//test./////txt/////\\//")
  // val path2 = Path("test/test.txt")
  println(path)
  println(os.pwd / path)

  // val guardian = ActorSystem(Orchestrator(), "task-orchestrator")
  // guardian.terminate()
end main

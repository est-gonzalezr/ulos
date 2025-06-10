/**
 * @author
 *   Esteban Gonzalez Ruales
 */

import actors.Orchestrator
import akka.actor.typed.ActorSystem
import os.Path
import os.RelPath

@main def main(): Unit =
  val _ = ActorSystem(Orchestrator(), "task-orchestrator")
  var x = null
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

import akka.actor.typed.ActorSystem
import actors.Orchestrator
import os.Path
import os.RelPath
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import scala.util.Try
import os.RelPath

@main def main(): Unit =

  val guardian = ActorSystem(Orchestrator(), "task-orchestrator")
  // guardian.terminate()

end main

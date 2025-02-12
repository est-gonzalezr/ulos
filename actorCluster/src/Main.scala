/** @author
  *   Esteban Gonzalez Ruales
  */

import actors.Orchestrator
import akka.actor.typed.ActorSystem
// import org.apache.pekko.actor.typed.ActorSystem
// import com.github.dockerjava.api.DockerClient
// import com.github.dockerjava.core.DockerClientBuilder
// import com.github.dockerjava.core.DefaultDockerClientConfig
// import com.github.dockerjava.core.command.BuildImageResultCallback
// import com.github.dockerjava.api.command.CreateContainerResponse
// import com.github.dockerjava.api.model.WaitResponse
// import org.apache.commons.net.ftp.FTP
// import org.apache.commons.net.ftp.FTPClient
// import utilities.DockerHelper
// import utilities.DockerUtil
// import scala.util.Try
// import os.Path

@main def main(): Unit =
  val _ = ActorSystem(Orchestrator(), "task-orchestrator")
  // guardian.terminate()

  // val _ =
  //   DockerUtil.runContainer(
  //     "cypress/included",
  //     Seq("run", "-b", "electron")
  //   )

end main

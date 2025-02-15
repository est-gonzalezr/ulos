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
import os.Path
import os.RelPath

@main def main(): Unit =
  val _ = ActorSystem(Orchestrator(), "task-orchestrator")
  // guardian.terminate()

  // def commonPrefix(p1: Path, p2: Path): Option[Path] =
  //   val segments1 = p1.segments.toVector
  //   val segments2 = p2.segments.toVector
  //   val commonSegments =
  //     segments1.zip(segments2).takeWhile((a, b) => a == b).map(_(1))

  //   if commonSegments.nonEmpty then
  //     Some(os.Path(commonSegments.mkString("/", "/", "")))
  //   else None
  //   end if
  // end commonPrefix

  // val p1 = Path("/cypress/testing/")
  // val p2 = Path("/cypress/testing/files/1.txt")

  // val segments1 = p1.segments.toVector
  // val segments2 = p2.segments.toVector
  // val commonSegments =
  //   segments1.zip(segments2).takeWhile((a, b) => a == b).map(_(1))
  // println(segments1.zip(segments2))
  // println(segments1.zip(segments2).takeWhile(_ == _).map(_(0)))

  // println(commonSegments.mkString("/", "/", ""))

  // val x =
  //   if commonSegments.nonEmpty then
  //     Some(Path(commonSegments.mkString("/", "/", "")))
  //   else None

  // println(x)

  // println(x.segments.toVector)

  // val z = commonPrefix(x, y)
  // println(z)

  // val _ =
  //   DockerUtil.runContainer(
  //     "cypress/included",
  //     Seq("run", "-b", "electron")
  //   )

end main

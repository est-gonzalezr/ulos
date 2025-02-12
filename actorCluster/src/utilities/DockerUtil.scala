package utilities

/** @author
  *   Esteban Gonzalez Ruales
  */

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.api.model.WaitResponse
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder

import scala.collection.mutable.ListBuffer

object DockerUtil:
  // val config = DefaultDockerClientConfig.createDefaultConfigBuilder().build()
  val dockerClient = DockerClientBuilder.getInstance().build()

  def runContainer(image: String, cmd: Seq[String]): (String, Int, String) =
    val containerFolder = "/cypress_testing"
    dockerClient
      .listContainersCmd()
      .withShowAll(true)
      .withShowAll(true)
      .exec()
      .forEach(container => println(container.getId))

    val container = dockerClient
      .createContainerCmd("cypress/included")
      .withCmd("run", "-b", "electron")
      .withWorkingDir("/cypress")
      // .withName("cypress_container")
      .withHostConfig(
        HostConfig()
          .withBinds(
            Bind.parse(
              "/Users/estebangonzalezruales/Downloads/ulos/cypress:/cypress"
            )
          )
          .withAutoRemove(true)
      )
      .exec()

    dockerClient.startContainerCmd(container.getId()).exec()

    // Capture logs
    val logBuffer = ListBuffer[String]()
    val logCallback = new ResultCallback.Adapter[Frame] {
      override def onNext(frame: Frame): Unit =
        val logLine = new String(frame.getPayload)
        logBuffer += logLine
        println(logLine) // Print logs in real-time
      end onNext
    }

    val logStream = dockerClient
      .logContainerCmd(container.getId)
      .withStdOut(true)
      .withStdErr(true)
      .withFollowStream(true)
      .exec(logCallback)

    try
      @volatile var exitCode: Int = -1 // Default to -1
      // Wait for container to finish and capture exit code
      val waitCallback = new ResultCallback.Adapter[WaitResponse] {

        override def onNext(response: WaitResponse): Unit =
          exitCode = response.getStatusCode

        def getExitCode: Int = exitCode
      }

      dockerClient.waitContainerCmd(container.getId).exec(waitCallback)
      waitCallback.awaitCompletion()

      println(s"Container exited with code $exitCode")
      (container.getId, exitCode, logBuffer.mkString("\n"))
    finally logStream.close() // Ensure log stream is closed properly
    end try
    // ("", -1, "")
  end runContainer
end DockerUtil

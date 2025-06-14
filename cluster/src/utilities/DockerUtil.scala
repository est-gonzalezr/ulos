package utilities

/**
 * @author
 *   Esteban Gonzalez Ruales
 */

import scala.collection.mutable.ListBuffer

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.WaitResponse
import com.github.dockerjava.core.DockerClientBuilder
import os.Path

object DockerUtil:
  val dockerClient: DockerClient = DockerClientBuilder.getInstance().build()

  def filteredCommand(cmd: String): Seq[String] =
    cmd
      .split(" ")
      .toSeq
      .filter(_ != "-it")
      .filter(_ != "-d")

  def runContainer(
    bindFileLocalPath: Path,
    image: String,
    workingDir: String,
    cmd: String,
  ): (String, Int, String) =
    // val workingDir = "/temp"
    val cmdSeq = filteredCommand(cmd)

    dockerClient
      .listContainersCmd()
      .withShowAll(true)
      .withShowAll(true)
      .exec()

    val container = dockerClient
      .createContainerCmd(image)
      .withCmd(cmdSeq*)
      .withWorkingDir(workingDir)
      .withHostConfig(
        HostConfig()
          .withBinds(
            Bind.parse(
              s"${bindFileLocalPath.toString}:$workingDir",
            ),
          )
          .withAutoRemove(true),
      )
      .exec()

    dockerClient.startContainerCmd(container.getId()).exec()

    // Capture logs
    val logBuffer = ListBuffer[String]()
    val logCallback = new ResultCallback.Adapter[Frame]:
      override def onNext(frame: Frame): Unit =
        val logLine = new String(frame.getPayload)
        logBuffer += logLine
        println(logLine) // Print logs in real-time
      end onNext

    val logStream = dockerClient
      .logContainerCmd(container.getId)
      .withStdOut(true)
      .withStdErr(true)
      .withFollowStream(true)
      .exec(logCallback)

    try
      @volatile var exitCode: Int = -1 // Default to -1
      // Wait for container to finish and capture exit code
      val waitCallback = new ResultCallback.Adapter[WaitResponse]:

        override def onNext(response: WaitResponse): Unit =
          exitCode = response.getStatusCode

      dockerClient.waitContainerCmd(container.getId).exec(waitCallback)
      waitCallback.awaitCompletion()

      println(s"Container exited with code $exitCode")
      (container.getId, exitCode, logBuffer.mkString("\n"))
    finally logStream.close() // Ensure log stream is closed properly
    end try
  end runContainer
end DockerUtil

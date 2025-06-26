package executors

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.WaitResponse
import com.github.dockerjava.core.DockerClientBuilder
import os.Path
import types.Task

import scala.collection.mutable.ListBuffer

class CypressGrammarExecutor(task: Task, absFilesDir: Path)
    extends Executor(task, absFilesDir):

  def execute(): Boolean =
    val image = "cypress-grammar"
    val cmdSeq = List("run")

    val dockerClient: DockerClient = DockerClientBuilder.getInstance().build()

    val container = dockerClient
      .createContainerCmd(image)
      .withCmd(cmdSeq*)
      .withEnv("FILES_DIR=cypress/e2e", "FILE_EXT=cy.js")
      .withHostConfig(
        HostConfig()
          .withBinds(
            Bind.parse(
              s"${absFilesDir.toString}:/mnt/tests/"
            )
          )
          .withAutoRemove(true)
      )
      .exec()

    dockerClient.startContainerCmd(container.getId()).exec()

    val logBuffer = ListBuffer[String]()
    val logCallback = new ResultCallback.Adapter[Frame]:
      override def onNext(frame: Frame): Unit =
        val logLine = String(frame.getPayload)
        logBuffer += logLine
        println(logLine)
      end onNext

    val logStream = dockerClient
      .logContainerCmd(container.getId)
      .withStdOut(true)
      .withStdErr(true)
      .withFollowStream(true)
      .exec(logCallback)

    @volatile var exitCode = -1
    try
      // Wait for container to finish and capture exit code
      val waitCallback = new ResultCallback.Adapter[WaitResponse]:
        override def onNext(response: WaitResponse): Unit =
          exitCode = response.getStatusCode()

      dockerClient.waitContainerCmd(container.getId()).exec(waitCallback)
      waitCallback.awaitCompletion()

    finally logStream.close()
    end try

    val passedLogPath = absFilesDir / "results" / "passed.log"

    if os.exists(passedLogPath) then
      println("Passed log found")
      exitCode = 0
      val _ = os.remove(passedLogPath)
    else
      println("No passed log found")
      exitCode = -1
    end if

    os.write.over(
      absFilesDir / s"output_${task.routingTree.get.exchange}_${task.routingTree.get.routingKey}.txt",
      logBuffer.mkString("\n"),
      createFolders = true
    )

    exitCode == 0

  end execute
end CypressGrammarExecutor

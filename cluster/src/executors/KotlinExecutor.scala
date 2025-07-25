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

class KotlinExecutor(task: Task, absFilesDir: Path)
    extends Executor(task, absFilesDir):

  def execute(): Boolean =
    val image = "android-deployer"
    val workingDir = "/app/"
    val cmdSeq = List("run")

    val dockerClient: DockerClient = DockerClientBuilder.getInstance().build()

    val container = dockerClient
      .createContainerCmd(image)
      .withCmd(cmdSeq*)
      .withWorkingDir(workingDir)
      .withEnv(
        "INNER_DIR=",
        "APK_REL=/app/build/outputs/apk/debug/app-debug.apk",
        "PACKAGE=com.example.budgetbuddy.MainActivity",
        "ADB_HOST=127.0.0.1",
        "ADB_PORT=5555"
      )
      .withHostConfig(
        HostConfig()
          .withBinds(
            Bind.parse(
              s"${absFilesDir.toString}:$workingDir"
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

    @volatile var exitCode = 0
    try
      // Wait for container to finish and capture exit code
      val waitCallback = new ResultCallback.Adapter[WaitResponse]:
        override def onNext(response: WaitResponse): Unit =
          exitCode = response.getStatusCode()

      dockerClient.waitContainerCmd(container.getId()).exec(waitCallback)
      waitCallback.awaitCompletion()

    finally logStream.close()
    end try

    os.write.over(
      absFilesDir / s"output_${task.routingTree.get.exchange}_${task.routingTree.get.routingKey}.txt",
      logBuffer.mkString("\n"),
      createFolders = true
    )

    exitCode == 0
  end execute
end KotlinExecutor

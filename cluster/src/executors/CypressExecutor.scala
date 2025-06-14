package executors

import os.Path
import types.Task
import utilities.DockerUtil

object CypressExecutor extends Executor:
  def execute(filePath: Path, task: Task): Task =
    // val (containerId: String, exitCode: Int, output: String) =
    //   DockerUtil.runContainer(filePath, "cypress/included", "run -b electron")

    val (containerId: String, exitCode: Int, output: String) =
      DockerUtil.runContainer(filePath, "cypress-executor", "/mnt/tests", "run")

    println("------------------------------------------")
    println("Here is a container for start")
    println(containerId)
    println(exitCode)
    println(output)
    println("here is a container for stop")
    println("------------------------------------------")

    os.write.over(
      filePath / s"output_${task.routingKeys.head}.txt",
      output,
    )

    task
  end execute

end CypressExecutor

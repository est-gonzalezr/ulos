package executors

import os.Path
import types.Task
import utilities.FileSystemUtil

object MockSuccessExecutor extends Executor:
  def execute(bindFileLocalPath: Path, task: Task): Boolean =
    println(
      s"[${Thread.currentThread().getName}] Simulating execution delay..."
    )
    Thread.sleep(5000)
    println(s"[${Thread.currentThread().getName}] Finished simulating delay.")

    val _ = FileSystemUtil.saveFile(
      task.relTaskFileDir / s"output_${task.routingKeys.head}.txt",
      s"Mock execution through ${task.routingKeys.head}.".getBytes().toSeq
    )

    true
  end execute

end MockSuccessExecutor

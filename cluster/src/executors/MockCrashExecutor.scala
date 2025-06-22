package executors

import os.Path
import types.Task
import utilities.FileSystemUtil

object MockCrashExecutor extends Executor:
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

    throw new RuntimeException("Mock crash")

    true
  end execute

end MockCrashExecutor

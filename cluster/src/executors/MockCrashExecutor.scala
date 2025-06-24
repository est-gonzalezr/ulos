package executors

import os.Path
import types.Task

class MockCrashExecutor(task: Task, absFilesDir: Path)
    extends Executor(task, absFilesDir):

  def execute(): Boolean =
    println(
      s"[${Thread.currentThread().getName}] Simulating execution delay..."
    )
    Thread.sleep(5000)
    println(s"[${Thread.currentThread().getName}] Finished simulating delay.")

    os.write.over(
      absFilesDir / s"output_${task.routingKeys.head}.txt",
      s"Mock execution through ${task.routingKeys.head}.".getBytes(),
      createFolders = true
    )

    throw new RuntimeException("Mock crash")

    true
  end execute

end MockCrashExecutor

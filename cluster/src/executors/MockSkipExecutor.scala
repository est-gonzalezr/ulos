package executors

import os.Path
import types.Task

class MockSkipExecutor(task: Task, absFilesDir: Path)
    extends Executor(task, absFilesDir):

  def execute(): Boolean =
    println("Skipping task")
    os.write.over(
      absFilesDir / s"output_${task.routingTree.get.exchange}_${task.routingTree.get.routingKey}.txt",
      s"Mock skip.".getBytes(),
      createFolders = true
    )

    true
  end execute
end MockSkipExecutor

package executors

import os.Path
import types.Task

class MockSkipExecutor(task: Task, absFilesDir: Path)
    extends Executor(task, absFilesDir):

  def execute(): Boolean = true
end MockSkipExecutor

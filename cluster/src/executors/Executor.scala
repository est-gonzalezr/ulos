package executors

import os.Path
import types.Task

trait Executor:
  def execute(filePath: Path, task: Task): Task
end Executor

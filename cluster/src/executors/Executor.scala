package executors

import os.Path
import types.Task

trait Executor:
  def execute(bindFileLocalPath: Path, task: Task): Boolean
end Executor

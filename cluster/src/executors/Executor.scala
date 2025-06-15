package executors

import scala.util.Try

import os.Path
import types.Task

trait Executor:
  def execute(bindFileLocalPath: Path, task: Task): Try[Task]
end Executor

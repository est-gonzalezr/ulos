package executors

import os.Path
import types.Task

import scala.annotation.nowarn

@nowarn trait Executor(task: Task, absFilesDir: Path):
  def execute(): Boolean
end Executor

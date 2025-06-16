package executors

import scala.util.Try

import os.Path
import types.Task

object MockExecutor extends Executor:
  def execute(bindFileLocalPath: Path, task: Task): Try[Task] =
    Try {

      println(s"[${Thread.currentThread().getName}] Simulating execution delay...")
      Thread.sleep(5000)
      println(s"[${Thread.currentThread().getName}] Finished simulating delay.")

      os.write.over(
        bindFileLocalPath / s"output_${task.routingKeys.head}.txt",
        s"Mock execution through ${task.routingKeys.head}.",
      )

      task
    }
  end execute

end MockExecutor

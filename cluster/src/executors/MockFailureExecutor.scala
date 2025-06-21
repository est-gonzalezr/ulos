package executors

import os.Path
import types.Task

object MockFailureExecutor extends Executor:
  def execute(bindFileLocalPath: Path, task: Task): Boolean =
    println(
      s"[${Thread.currentThread().getName}] Simulating execution delay..."
    )
    Thread.sleep(5000)
    println(s"[${Thread.currentThread().getName}] Finished simulating delay.")

    os.write.over(
      bindFileLocalPath / s"output_${task.routingKeys.head}.txt",
      s"Mock execution through ${task.routingKeys.head}."
    )

    false
  end execute

end MockFailureExecutor

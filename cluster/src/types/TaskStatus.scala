package types

import zio.json.JsonDecoder
import zio.json.JsonEncoder

enum TaskStatus:
  case Processing, Successful, Failed, Crashed
end TaskStatus

object TaskStatus:
  given JsonDecoder[TaskStatus] = JsonDecoder[String].mapOrFail(_ =>
    Right(TaskStatus.Processing) // default
  )

  given JsonEncoder[TaskStatus] = JsonEncoder[String].contramap(_.toString)
end TaskStatus

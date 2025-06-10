package types

/**
 * @author
 *   Esteban Gonzalez Ruales
 */

import os.Path
import zio.json.DeriveJsonDecoder
import zio.json.DeriveJsonEncoder
import zio.json.JsonDecoder
import zio.json.JsonEncoder

final case class TaskDefinition(
  definitionName: String,
  stages: List[Tuple3[String, Path, String]],
)

object TaskDefinition:
  implicit val pathDecoder: JsonDecoder[Path] =
    JsonDecoder[String].map(os.Path(_))

  implicit val taskDefinitionDecoder: JsonDecoder[TaskDefinition] =
    DeriveJsonDecoder.gen[TaskDefinition]

  implicit val pathEncoder: JsonEncoder[Path] =
    JsonEncoder[String].contramap(_.toString)
  implicit val taskDefinitionEncoder: JsonEncoder[TaskDefinition] =
    DeriveJsonEncoder.gen[TaskDefinition]
end TaskDefinition

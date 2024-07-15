import mill._
import mill.scalalib._

trait ProjectConfigs extends ScalaModule {
  def scalaVersion = "3.4.2"

  // def env = Map(
  //   "RABBITMQ_HOST" -> "localhost",
  //   "RABBITMQ_PORT" -> "1234",
  //   "RABBITMQ_USERNAME" -> "guest",
  //   "RABBITMQ_PASSWORD" -> "guest"
  // )

  def scalacOptions = Seq(
    // "-verbose",
    "-explain",
    "-deprecation",
    "-unchecked",
    "-Wunused:all",
    "-Wnonunit-statement",
    "-Wvalue-discard"
    // "-Werror"
  )
}

object coreUtils extends ProjectConfigs {
  def ivyDeps = Agg(
    ivy"org.scalameta::munit::1.0.0-M11",
    ivy"org.scala-lang::toolkit::0.4.0",
    ivy"org.typelevel::cats-effect::3.6-0142603",
    ivy"org.virtuslab::scala-yaml::0.1.0",
    ivy"com.rabbitmq:amqp-client:5.21.0"
  )
}

object globalProcessing extends ProjectConfigs {
  def moduleDeps = Seq(coreUtils)
  def ivyDeps = Agg(
    ivy"org.scalameta::munit::1.0.0-M11",
    ivy"org.typelevel::cats-effect::3.6-0142603",
    ivy"com.rabbitmq:amqp-client:5.21.0"
  )
}

// object processingCluster extends ProjectConfigs {
//   def ivyDeps = Agg(
//     ivy"org.scalameta::munit::1.0.0-M11",
//     ivy"org.scala-lang::toolkit::0.4.0",
//     ivy"org.typelevel::cats-effect::3.6-0142603",
//     ivy"com.rabbitmq:amqp-client:5.21.0",
//     ivy"commons-net:commons-net:3.10.0"
//   )
// }

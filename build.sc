import mill._
import mill.scalalib._

val scalaToolkit = ivy"org.scala-lang::toolkit::0.4.0"
val catsEffect = ivy"org.typelevel::cats-effect::3.6-0142603"
val fs2 = ivy"co.fs2::fs2-core::3.1.6"
val munit = ivy"org.scalameta::munit::1.0.0-M11"
val scalaYaml = ivy"org.virtuslab::scala-yaml::0.3.0"
val amqpClient = ivy"com.rabbitmq:amqp-client:5.21.0"
val apacheCommonsNet = ivy"commons-net:commons-net:3.10.0"
val skunk = ivy"org.tpolecat::skunk-core::1.1.0-M3"

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
    "-Wvalue-discard",
    "-Werror"
  )
}

object coreUtils extends ProjectConfigs {
  def ivyDeps = Agg(
    munit,
    scalaToolkit,
    catsEffect,
    scalaYaml,
    amqpClient
  )
}

object storageUtils extends ProjectConfigs {
  def ivyDeps = Agg(
    apacheCommonsNet,
    catsEffect
  )
}

object globalProcessing extends ProjectConfigs {
  def moduleDeps = Seq(coreUtils, storageUtils)
}

object executionCluster extends ProjectConfigs {
  def moduleDeps = Seq(coreUtils, storageUtils)
}

object parsingCluster extends ProjectConfigs {
  def moduleDeps = Seq(coreUtils, storageUtils)
}

object databaseManagement extends ProjectConfigs {
  def moduleDeps = Seq(coreUtils)
  def ivyDeps = Agg(
    skunk
  )
}

// object testingGround extends ProjectConfigs {
//   def ivyDeps = Agg(
//     // fs2, cats-effect
//     ivy"org.typelevel::cats-effect::3.6-0142603",
//     ivy"co.fs2::fs2-core::3.1.6"
//   )
// }

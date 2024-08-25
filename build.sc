import mill._
import mill.scalalib._

val scalaToolkit = ivy"org.scala-lang::toolkit::0.5.0"
val catsEffect = ivy"org.typelevel::cats-effect::3.5.4"
val fs2 = ivy"co.fs2::fs2-core::3.1.6"
val munit = ivy"org.scalameta::munit::1.0.1"
val scalaYaml = ivy"org.virtuslab::scala-yaml::0.3.0"
val amqpClient = ivy"com.rabbitmq:amqp-client:5.21.0"
val apacheCommonsNet = ivy"commons-net:commons-net:3.10.0"
val skunk = ivy"org.tpolecat::skunk-core::0.6.4"

trait ProjectConfigs extends ScalaModule {
  def scalaVersion = "3.5.0"
  def scalacOptions = Seq(
    // "-verbose",
    "-explain",
    "-deprecation",
    "-unchecked",
    "-Wunused:all",
    "-Wnonunit-statement",
    "-Wvalue-discard",
    "-Werror",
    "-new-syntax",
    "-rewrite"
  )

  def scalaDocOptions = Seq("-siteroot", "mydocs", "-no-link-warnings")
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

object brokerManagement extends ProjectConfigs {
  def moduleDeps = Seq(coreUtils, storageUtils)
  def forkEnv = Map(
    "RABBITMQ_HOST" -> "localhost",
    "RABBITMQ_PORT" -> "5672",
    "RABBITMQ_USER" -> "guest",
    "RABBITMQ_PASS" -> "guest"
  )
}

object databaseCluster extends ProjectConfigs {
  def moduleDeps = Seq(coreUtils)
  def ivyDeps = Agg(
    skunk
  )
  def forkEnv = Map(
    "RABBITMQ_HOST" -> "localhost",
    "RABBITMQ_PORT" -> "5672",
    "RABBITMQ_USER" -> "guest",
    "RABBITMQ_PASS" -> "guest",
    "PRIMARY_EXCHANGE" -> "processing_exchange",
    "CONSUMPTION_QUEUE" -> "database_queue"
  )
}

object parsingCluster extends ProjectConfigs {
  def moduleDeps = Seq(coreUtils, storageUtils)
  def forkEnv = Map(
    "RABBITMQ_HOST" -> "localhost",
    "RABBITMQ_PORT" -> "5672",
    "RABBITMQ_USER" -> "guest",
    "RABBITMQ_PASS" -> "guest",
    "PRIMARY_EXCHANGE" -> "processing_exchange",
    "CONSUMPTION_QUEUE" -> "parsing_queue",
    "PUBLISHING_ROUTING_KEY" -> "task.execution",
    "DATABASE_ROUTING_KEY" -> "database.task.update"
  )
}

object executionCluster extends ProjectConfigs {
  def moduleDeps = Seq(coreUtils, storageUtils)
  def forkEnv = Map(
    "RABBITMQ_HOST" -> "localhost",
    "RABBITMQ_PORT" -> "5672",
    "RABBITMQ_USER" -> "guest",
    "RABBITMQ_PASS" -> "guest",
    "PRIMARY_EXCHANGE" -> "processing_exchange",
    "CONSUMPTION_QUEUE" -> "execution_testing_queue",
    "PUBLISHING_ROUTING_KEY" -> "user.notification",
    "DATABASE_ROUTING_KEY" -> "database.task.update"
  )
}

// object testingGround extends ProjectConfigs {
//   def ivyDeps = Agg(
//     // fs2, cats-effect
//     ivy"org.typelevel::cats-effect::3.6-0142603",
//     ivy"co.fs2::fs2-core::3.1.6"
//   )
// }

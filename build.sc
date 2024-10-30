import coursier.Repository
import mill._
import mill.scalalib._
import mill.define.ModuleRef
import coursier.maven.MavenRepository

val AkkaVersion = "2.10.0"

val scalaToolkit = ivy"org.scala-lang::toolkit::0.5.0"
val catsEffect = ivy"org.typelevel::cats-effect::3.5.4"
val fs2 = ivy"co.fs2::fs2-core::3.1.6"
val munit = ivy"org.scalameta::munit::1.0.1"
val scalaYaml = ivy"org.virtuslab::scala-yaml::0.3.0"
val amqpClient = ivy"com.rabbitmq:amqp-client:5.21.0"
val apacheCommonsNet = ivy"commons-net:commons-net:3.10.0"
val skunk = ivy"org.tpolecat::skunk-core::0.6.4"
val akka = ivy"com.typesafe.akka::akka-actor-typed::$AkkaVersion"
val akkaTestKit = ivy"com.typesafe.akka::akka-actor-testkit-typed::$AkkaVersion"
val logback = ivy"ch.qos.logback:logback-classic:1.5.8"
val scalaLogging = ivy"com.typesafe.scala-logging::scala-logging::3.9.4"
val zio = ivy"dev.zio::zio::2.1.9"
val zioJson = ivy"dev.zio::zio-json::0.7.3"

val akkaRepository = Seq(
  MavenRepository("https://repo.akka.io/maven")
)

trait ProjectConfigs extends ScalaModule {
  def scalaVersion = "3.5.0"
  def scalacOptions = Seq(
    // "-verbose",
    // "-explain",
    "-deprecation",
    "-unchecked",
    // "-Wunused:all",
    "-Wnonunit-statement",
    "-Wvalue-discard",
    "-Werror",
    "-new-syntax",
    "-rewrite"
  )

  def scalaDocOptions = Seq("-siteroot", "mydocs", "-no-link-warnings")
  def repositoriesTask: Task[Seq[Repository]] = T.task {
    super.repositoriesTask() ++ akkaRepository
  }
}

object coreUtils extends ProjectConfigs {
  def ivyDeps: Target[Agg[Dep]] = Agg(
    munit,
    scalaToolkit,
    zio,
    catsEffect,
    scalaYaml,
    amqpClient,
    logback,
    scalaLogging
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

object testingGround extends ProjectConfigs {
  def moduleDeps = Seq(coreUtils, storageUtils)
  def ivyDeps = Agg(
    amqpClient,
    akka,
    akkaTestKit,
    zioJson
  )

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

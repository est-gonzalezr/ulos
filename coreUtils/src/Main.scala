// import cats.effect.IO
// import cats.effect.unsafe.implicits.global
// import messaging.MessagingUtil.brokerConnection
// import messaging.MessagingUtil.channelFromConnection
// import org.virtuslab.yaml.*
// import types.YamlExchange
// import types.YamlQueue
// import configuration.BrokerConfigurationUtil.getBrokerEnvironmentVariables
// import com.rabbitmq.client.Connection
// import configuration.ExternalConfigurationUtil.stringFromFilepath
// import configuration.BrokerConfigurationUtil.configureBrokerExchanges
// import configuration.BrokerConfigurationUtil.configureBrokerQueues

// @main
// def main(args: String*): Unit =
//   println("Hello world!")
//   program.unsafeRunSync()
//   ()

// def program: IO[Unit] =
//   for
//     connection <- createConnection
//     channel <- channelFromConnection(connection)

//     exchangesFile <- stringFromFilepath(
//       os.pwd / "globalProcessing" / "resources" / "exchanges.yaml"
//     )
//     exchanges <- IO.fromOption(
//       exchangesFile.as[Map[String, YamlExchange]].toOption
//     )(Exception("exchanges not found"))

//     queuesFile <- stringFromFilepath(
//       os.pwd / "globalProcessing" / "resources" / "queues.yaml"
//     )
//     queues <- IO.fromOption(
//       queuesFile.as[Map[String, YamlQueue]].toOption
//     )(Exception("queues not found"))

//     _ <- configureBrokerExchanges(channel, exchanges)
//     _ <- configureBrokerQueues(channel, queues)
//   yield ()
//   // IO.delay {
//   //   val connection = createConnection
//   //   val channel = connection.flatMap(channelFromConnection)
//   //   val yamlExchanges =
//   //     for
//   //       yaml <- stringFromFilepath(
//   //         os.pwd / "globalProcessing" / "resources" / "exchanges.yaml"
//   //       )
//   //       yamlExchanges <- IO.fromOption(
//   //         yaml.as[Map[String, YamlExchange]].toOption
//   //       )(Exception("exchanges not found"))
//   //     yield yamlExchanges

//   //   val yamlQueues =
//   //     for
//   //       yaml <- stringFromFilepath(
//   //         os.pwd / "globalProcessing" / "resources" / "queues.yaml"
//   //       )
//   //       yamlQueues <- IO.fromOption(
//   //         yaml.as[Map[String, YamlQueue]].toOption
//   //       )(Exception("queues not found"))
//   //     yield yamlQueues

//   //   val _ =
//   //     for
//   //       channel <- channel
//   //       exchanges <- yamlExchanges
//   //     yield configureBrokerExchanges(channel, exchanges)

//   //   val _ =
//   //     for
//   //       channel <- channel
//   //       queues <- yamlQueues
//   //     yield configureBrokerQueues(channel, queues)
//   // }

// def createConnection: IO[Connection] =
//   for
//     env <- getBrokerEnvironmentVariables
//     host <- IO.fromOption(env.get("host"))(Exception("host not found"))
//     port <- IO.fromOption(env.get("port"))(Exception("port not found"))
//     user <- IO.fromOption(env.get("user"))(Exception("user not found"))
//     pass <- IO.fromOption(env.get("pass"))(Exception("pass not found"))
//     connection <- brokerConnection(
//       host,
//       port.toInt,
//       user,
//       pass
//     )
//   yield connection

// // println(os.pwd / "globalProcessing" / "resources" / "queues.yaml")
// // println(os.read(os.pwd / "globalProcessing" / "resources" / "queues.yaml"))
// // val queues =
// //   os.read(os.pwd / "globalProcessing" / "resources" / "queues.yaml")
// // val yaml = queues.as[Map[String, YamlQueue]]
// // println(yaml)

// // val exchanges =
// //   os.read(os.pwd / "globalProcessing" / "resources" / "exchanges.yaml")
// // val yamlExchanges = exchanges.as[Map[String, YamlExchange]]
// // println(yamlExchanges)

// // println(rabbitmqHost.unsafeRunSync())

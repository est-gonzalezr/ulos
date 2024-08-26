// import fs2.*
// import cats.effect.*
// import cats.effect.unsafe.implicits.global
// import scala.concurrent.duration.*
// import cats.syntax.parallel.catsSyntaxParallelSequence1
// import cats.syntax.traverse.toTraverseOps
// import cats.effect.std.Console
// import com.rabbitmq.client.ConnectionFactory
// import com.rabbitmq.client.DefaultConsumer
// import com.rabbitmq.client.Envelope
// import com.rabbitmq.client.AMQP
// import messaging.MessagingUtil.brokerConnection
// import messaging.MessagingUtil.channelFromConnection
// import messaging.MessagingUtil.consumeMessages
// import types.OpaqueTypes.QueueName
// import com.rabbitmq.client.Channel
// import startup.ConsumerProgram
// import com.rabbitmq.client.Connection
// import configuration.MiscConfigUtil.brokerEnvVars

// val DefaultProcessingConsumerQuantity = 5
// val queueName = QueueName("parsing_queue")

// // object TestingCluster extends IOApp:
// //   def run(args: List[String]): IO[ExitCode] =
// //     val consumerAmount = args.headOption
// //       .flatMap(_.toIntOption)
// //       .getOrElse(DefaultProcessingConsumerQuantity)
// //     mainProgramHandler(consumerAmount).as(ExitCode.Success)
// //   end run

// //   def createConsumer(channel: Channel): IO[DefaultConsumer] =
// //     val consumer = new DefaultConsumer(channel) {
// //       override def handleDelivery(
// //           consumerTag: String,
// //           envelope: Envelope,
// //           properties: AMQP.BasicProperties,
// //           body: Array[Byte]
// //       ): Unit =
// //         val message = new String(body, "UTF-8")
// //         println(s"Received '$message'")
// //       end handleDelivery
// //     }
// //     IO.pure(consumer)
// //   end createConsumer

// //   def mainProgramHandler(consumerAmount: Int): IO[Nothing] =
// //     mainProgram(consumerAmount)
// //       .handleErrorWith(Console[IO].printStackTrace)
// //       .foreverM

// //   def mainProgram(consumerAmount: Int): IO[Unit] =
// //     initializeProgram(
// //       Option(consumerAmount)
// //         .filter(_ > 0)
// //         .getOrElse(DefaultProcessingConsumerQuantity)
// //     )

// //   def initializeProgram(consumerAmount: Int): IO[Unit] =
// //     for
// //       envVars <- brokerEnvVars
// //       host <- IO.fromOption(envVars.get("host"))(Exception("host not found"))
// //       port <- IO.fromOption(envVars.get("port"))(Exception("port not found"))
// //       user <- IO.fromOption(envVars.get("user"))(Exception("user not found"))
// //       pass <- IO.fromOption(envVars.get("pass"))(Exception("pass not found"))
// //       portInt <- IO.fromOption(port.toIntOption)(Exception("port not an int"))
// //       _ <- brokerConnection(host, portInt, user, pass).use(connection =>
// //         createQueueConsumers(connection, consumerAmount).void
// //       )
// //     yield ()

// //   def queueConsumerProgramHandler(connection: Connection): IO[Nothing] =
// //     queueConsumerProgram(connection)
// //       .handleErrorWith(Console[IO].printStackTrace)
// //       .foreverM

// //   def createQueueConsumers(
// //       connection: Connection,
// //       quantity: Int
// //   ): IO[List[Unit]] =
// //     List
// //       .fill(quantity)(
// //         queueConsumerProgramHandler(connection)
// //       )
// //       .parSequence()

// //   def queueConsumerProgram(connection: Connection): IO[Unit] =
// //     channelFromConnection(connection)
// //       .use(channel =>
// //         for
// //           consumer <- createConsumer(channel)
// //           _ <- consumeMessages(
// //             channel,
// //             queueName,
// //             consumer,
// //             false
// //           )
// //           _ <- IO.delay(Thread.sleep(60000)).foreverM
// //         yield ()
// //       )
// // end TestingCluster

// // @main def main(args: String*): Unit =
// //   // val factory = new ConnectionFactory()
// //   // factory.setHost("localhost")
// //   // factory.setPort(5672)
// //   // factory.setUsername("guest")
// //   // factory.setPassword("guest")

// //   // val connection = factory.newConnection()
// //   // val channel = connection.createChannel()

// //   val queueName = QueueName("parsing_queue")

// //   def createConsumer(channel: Channel): IO[DefaultConsumer] =
// //     val consumer = new DefaultConsumer(channel) {
// //       override def handleDelivery(
// //           consumerTag: String,
// //           envelope: Envelope,
// //           properties: AMQP.BasicProperties,
// //           body: Array[Byte]
// //       ): Unit =
// //         val message = new String(body, "UTF-8")
// //         println(s"Received '$message'")
// //       end handleDelivery
// //     }
// //     IO.pure(consumer)
// //   end createConsumer

// //   // val _ =
// //   //   for
// //   //     consumer <- createConsumer(channel)
// //   //     _ <- brokerConnection("localhost", 5672, "guest", "guest").use(
// //   //       connection =>
// //   //         channelFromConnection(connection).use(channel =>
// //   //           List
// //   //             .fill(1)(
// //   //               consumeMessages(channel, queueName, consumer)
// //   //             )
// //   //             .parSequence
// //   //         )
// //   //     )
// //   //   yield ()

// //   val prog =
// //     for
// //       // consumer <- createConsumer(channel)
// //       _ <- brokerConnection("localhost", 5672, "guest", "guest").use(
// //         connection =>
// //           List
// //             .fill(3)(
// //               channelFromConnection(connection).use(channel =>
// //                 println("Consuming messages")
// //                 for
// //                   consumer <- createConsumer(channel)
// //                   _ <- consumeMessages(channel, queueName, consumer)
// //                   _ <- IO.sleep(60.second).foreverM
// //                 yield ()
// //                 end for
// //               )
// //             )
// //             .parSequence
// //       )
// //     yield ()

// //   prog.unsafeRunSync()

// //   // val _ =
// //   //   IO.delay(channel.basicConsume(queueName, true, consumer)).unsafeRunSync()
// // end main

// // @main def main(args: String*): Unit =

// //   val numbers = Stream(1, 2, 3, 4, 5).repeat
// //   val seconds = Stream.fixedRate[IO](1.second)
// //   val numbersWithWait = numbers.zipLeft(seconds)

// //   readAndAverage(0, 0).foreverM.unsafeRunSync()
// // end main

// // def readAndAverage(sum: Int, count: Int): IO[Unit] =
// //   for
// //     _ <- IO(println("Enter a number:"))
// //     number <- Console[IO].readLine.map(_.toInt)
// //     newSum = sum + number
// //     newCount = count + 1
// //     _ <- Console[IO].println(
// //       s"Sum: $newSum, Count: $newCount, Average: ${newSum / newCount}"
// //     )
// //     _ <- readAndAverage(newSum, newCount)
// //   yield ()

// // val a = for
// //   ref <- Ref.of[IO, Int](0)
// //   operation = ref.update(_ + 1)
// //   _ <- List(
// //     operation,
// //     operation,
// //     operation,
// //     numbersWithWait
// //       .take(5)
// //       .map(x =>
// //         println(x)
// //         x
// //       )
// //       .compile
// //       .drain
// //   ).parSequence
// //   res <- ref.get
// // yield res

// // val a = for
// //   ref <- Ref.of[IO, Int](0)
// //   _ <- List(
// //     getNumbersFromUser(ref),
// //     printNumbers(ref)
// //   ).parSequence
// // yield ()

// // println(a.unsafeRunSync())

// // val a = program
// // a.unsafeRunSync()

// // def getNumbersFromUser(ref: Ref[IO, Int]): IO[Unit] =
// //   (for
// //     _ <- IO.sleep(1.second)
// //     _ <- IO(println("Add 7"))
// //     // int <- IO(scala.io.StdIn.readInt())
// //     _ <- ref.update(_ + 7)
// //   yield ()).foreverM

// // def printNumbers(ref: Ref[IO, Int]): IO[Unit] =
// //   (for
// //     _ <- IO.sleep(1.second)
// //     _ <- ref.get.flatMap(x => IO(println(x)))
// //   yield ()).foreverM

// // case class A(a: IO[Int], b: IO[Unit])

// // def program: IO[A] =
// //   val a = for
// //     ref1 <- Ref.of[IO, Int](0)
// //     fiber <- List(
// //       getNumbersFromUser(ref1)
// //       // printNumbers(ref1)
// //     ).parSequence.start
// //   yield A(ref1.get, fiber.cancel)

// //   for
// //     p <- a
// //     _ <- IO.sleep(5.seconds)
// //     _ <- p.a.flatMap(x => IO(println(x)))
// //     _ <- p.b
// //     _ <- IO.sleep(5.seconds)
// //     _ <- p.a.flatMap(x => IO(println(x)))
// //   yield p
// //   end for
// // end program

// // numbersWithWait
// //   .take(6)
// //   .map(x =>
// //     println(x)
// //     x
// //   )
// //   .compile
// //   .drain
// //   .unsafeRunSync()

// // object model:
// //   opaque type City = String
// //   object City:
// //     def apply(name: String): City = name
// //     extension (city: City) def name: String = city

// //   case class CityStats(city: City, checkIns: Int)

// // import model.*

// // case class ProcessingCheckIns(
// //     currentRanking: IO[List[CityStats]],
// //     stop: IO[Unit]
// // )

// // def topCities(cityCheckIns: Map[City, Int]): List[CityStats] =
// //   cityCheckIns.toList
// //     .map(_ match
// //       case (city, checkIns) => CityStats(city, checkIns)
// //     )
// //     .sortBy(_.checkIns)
// //     .reverse
// //     .take(3)

// // def storeCheckIn(storedCheckIns: Ref[IO, Map[City, Int]])(
// //     city: City
// // ): IO[Unit] =
// //   storedCheckIns.update(_.updatedWith(city)(_ match
// //     case None           => Some(1)
// //     case Some(checkIns) => Some(checkIns + 1)
// //   ))

// // def updateRanking(
// //     storedCheckIns: Ref[IO, Map[City, Int]],
// //     storedRanking: Ref[IO, List[CityStats]]
// // ): IO[Nothing] =
// //   (for
// //     newRanking <- storedCheckIns.get.map(topCities)
// //     _ <- storedRanking.set(newRanking)
// //   yield ()).foreverM

// // def processCheckIns(checkIns: Stream[IO, City]): IO[ProcessingCheckIns] =
// //   for
// //     storedCheckIns <- Ref.of[IO, Map[City, Int]](Map.empty)
// //     storedRanking <- Ref.of[IO, List[CityStats]](List.empty)
// //     rankingProgram = updateRanking(storedCheckIns, storedRanking)
// //     checkInsProgram = checkIns
// //       .evalMap(storeCheckIn(storedCheckIns))
// //       .compile
// //       .drain
// //     fiber <- List(rankingProgram, checkInsProgram).parSequence.start
// //   yield ProcessingCheckIns(storedRanking.get, fiber.cancel)

// // def program(checkIns: Stream[IO, City]): IO[Nothing] =
// //   (for {
// //     processing <- processCheckIns(checkIns)
// //     ranking <- processing.currentRanking
// //     _ <- IO.println(ranking)
// //     _ <- IO.sleep(1.second)

// //     newRanking <- processing.currentRanking
// //     _ <- processing.stop
// //   } yield newRanking).foreverM

// // @main def main(args: String*): Unit =
// //   val checkIns: Stream[IO, City] =
// //     Stream(
// //       City("Sydney"),
// //       City("Dublin"),
// //       City("Cape Town"),
// //       City("Lima"),
// //       City("Singapore")
// //     )
// //       .repeatN(100_000)
// //       .append(Stream.range(0, 100_000).map(i => City(s"City $i")))
// //       .append(Stream(City("Sydney"), City("Sydney"), City("Lima")))
// //       .covary[IO]

// //   program(checkIns).unsafeRunSync()
// //   ()

// // @main def main(args: String*): Unit =
// //   println("Hello world!")

// //   val s1 = Stream(1, 2, 3)
// //   val s2 = s1.append(s1).append(s1)

// //   println(Stream.eval(s1))
// //   println(Stream.emit(1, 2, 3))
// //   val eff = Stream.eval(IO { println("BEING RUN!!"); 1 + 1 })
// //   println(eff.compile.toList.unsafeRunSync())

// //   val a = for
// //     _ <- IO.sleep(1.second)
// //     result <- List(throwDice, throwDice).parSequence
// //   yield result.sum
// //   println(a.unsafeRunSync())

// //   val b = for
// //     ref <- Ref.of[IO, List[Int]](List.empty)
// //     cast = throwDice.flatMap(result => ref.update(result :: _))
// //     _ <- List(cast, cast).parSequence
// //     casts <- ref.get
// //   yield casts

// //   println(b.unsafeRunSync())

// //   val c = for
// //     ref <- Ref.of[IO, List[Int]](List.empty)
// //     cast = throwDice.flatMap(result => ref.update(result :: _))
// //     _ <- List(cast, cast, cast).parSequence
// //     casts <- ref.get
// //   yield casts

// //   println(c.unsafeRunSync())

// //   val d = for
// //     ref <- Ref.of[IO, Int](0)
// //     cast = throwDice.flatMap(result =>
// //       if result == 6 then ref.update(_ + 1) else IO.unit
// //     )
// //     _ <- List.fill(100)(cast).parSequence
// //     total <- ref.get
// //   yield total

// //   println(d.unsafeRunSync())

// //   val e = List
// //     .fill(100)(IO.sleep(1.second).flatMap(_ => throwDice))
// //     .parSequence
// //     .map(_.sum)

// //   println(e.unsafeRunSync())

// //   ()

// // def throwDice: IO[Int] = IO {
// //   val r = scala.util.Random
// //   r.nextInt(6) + 1
// // }

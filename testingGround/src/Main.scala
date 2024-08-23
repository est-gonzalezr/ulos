import fs2.*
import cats.effect.*
import cats.effect.unsafe.implicits.global
import scala.concurrent.duration.*
import cats.syntax.parallel.catsSyntaxParallelSequence1
import cats.syntax.traverse.toTraverseOps
import cats.effect.std.Console

@main def main(args: String*): Unit =

  val numbers = Stream(1, 2, 3, 4, 5).repeat
  val seconds = Stream.fixedRate[IO](1.second)
  val numbersWithWait = numbers.zipLeft(seconds)

  readAndAverage(0, 0).foreverM.unsafeRunSync()
end main

def readAndAverage(sum: Int, count: Int): IO[Unit] =
  for
    _ <- IO(println("Enter a number:"))
    number <- Console[IO].readLine.map(_.toInt)
    newSum = sum + number
    newCount = count + 1
    _ <- Console[IO].println(
      s"Sum: $newSum, Count: $newCount, Average: ${newSum / newCount}"
    )
    _ <- readAndAverage(newSum, newCount)
  yield ()

  // val a = for
  //   ref <- Ref.of[IO, Int](0)
  //   operation = ref.update(_ + 1)
  //   _ <- List(
  //     operation,
  //     operation,
  //     operation,
  //     numbersWithWait
  //       .take(5)
  //       .map(x =>
  //         println(x)
  //         x
  //       )
  //       .compile
  //       .drain
  //   ).parSequence
  //   res <- ref.get
  // yield res

  // val a = for
  //   ref <- Ref.of[IO, Int](0)
  //   _ <- List(
  //     getNumbersFromUser(ref),
  //     printNumbers(ref)
  //   ).parSequence
  // yield ()

  // println(a.unsafeRunSync())

  // val a = program
  // a.unsafeRunSync()

def getNumbersFromUser(ref: Ref[IO, Int]): IO[Unit] =
  (for
    _ <- IO.sleep(1.second)
    _ <- IO(println("Add 7"))
    // int <- IO(scala.io.StdIn.readInt())
    _ <- ref.update(_ + 7)
  yield ()).foreverM

def printNumbers(ref: Ref[IO, Int]): IO[Unit] =
  (for
    _ <- IO.sleep(1.second)
    _ <- ref.get.flatMap(x => IO(println(x)))
  yield ()).foreverM

case class A(a: IO[Int], b: IO[Unit])

def program: IO[A] =
  val a = for
    ref1 <- Ref.of[IO, Int](0)
    fiber <- List(
      getNumbersFromUser(ref1)
      // printNumbers(ref1)
    ).parSequence.start
  yield A(ref1.get, fiber.cancel)

  for
    p <- a
    _ <- IO.sleep(5.seconds)
    _ <- p.a.flatMap(x => IO(println(x)))
    _ <- p.b
    _ <- IO.sleep(5.seconds)
    _ <- p.a.flatMap(x => IO(println(x)))
  yield p
  end for
end program

// numbersWithWait
//   .take(6)
//   .map(x =>
//     println(x)
//     x
//   )
//   .compile
//   .drain
//   .unsafeRunSync()

// object model:
//   opaque type City = String
//   object City:
//     def apply(name: String): City = name
//     extension (city: City) def name: String = city

//   case class CityStats(city: City, checkIns: Int)

// import model.*

// case class ProcessingCheckIns(
//     currentRanking: IO[List[CityStats]],
//     stop: IO[Unit]
// )

// def topCities(cityCheckIns: Map[City, Int]): List[CityStats] =
//   cityCheckIns.toList
//     .map(_ match
//       case (city, checkIns) => CityStats(city, checkIns)
//     )
//     .sortBy(_.checkIns)
//     .reverse
//     .take(3)

// def storeCheckIn(storedCheckIns: Ref[IO, Map[City, Int]])(
//     city: City
// ): IO[Unit] =
//   storedCheckIns.update(_.updatedWith(city)(_ match
//     case None           => Some(1)
//     case Some(checkIns) => Some(checkIns + 1)
//   ))

// def updateRanking(
//     storedCheckIns: Ref[IO, Map[City, Int]],
//     storedRanking: Ref[IO, List[CityStats]]
// ): IO[Nothing] =
//   (for
//     newRanking <- storedCheckIns.get.map(topCities)
//     _ <- storedRanking.set(newRanking)
//   yield ()).foreverM

// def processCheckIns(checkIns: Stream[IO, City]): IO[ProcessingCheckIns] =
//   for
//     storedCheckIns <- Ref.of[IO, Map[City, Int]](Map.empty)
//     storedRanking <- Ref.of[IO, List[CityStats]](List.empty)
//     rankingProgram = updateRanking(storedCheckIns, storedRanking)
//     checkInsProgram = checkIns
//       .evalMap(storeCheckIn(storedCheckIns))
//       .compile
//       .drain
//     fiber <- List(rankingProgram, checkInsProgram).parSequence.start
//   yield ProcessingCheckIns(storedRanking.get, fiber.cancel)

// def program(checkIns: Stream[IO, City]): IO[Nothing] =
//   (for {
//     processing <- processCheckIns(checkIns)
//     ranking <- processing.currentRanking
//     _ <- IO.println(ranking)
//     _ <- IO.sleep(1.second)

//     newRanking <- processing.currentRanking
//     _ <- processing.stop
//   } yield newRanking).foreverM

// @main def main(args: String*): Unit =
//   val checkIns: Stream[IO, City] =
//     Stream(
//       City("Sydney"),
//       City("Dublin"),
//       City("Cape Town"),
//       City("Lima"),
//       City("Singapore")
//     )
//       .repeatN(100_000)
//       .append(Stream.range(0, 100_000).map(i => City(s"City $i")))
//       .append(Stream(City("Sydney"), City("Sydney"), City("Lima")))
//       .covary[IO]

//   program(checkIns).unsafeRunSync()
//   ()

// @main def main(args: String*): Unit =
//   println("Hello world!")

//   val s1 = Stream(1, 2, 3)
//   val s2 = s1.append(s1).append(s1)

//   println(Stream.eval(s1))
//   println(Stream.emit(1, 2, 3))
//   val eff = Stream.eval(IO { println("BEING RUN!!"); 1 + 1 })
//   println(eff.compile.toList.unsafeRunSync())

//   val a = for
//     _ <- IO.sleep(1.second)
//     result <- List(throwDice, throwDice).parSequence
//   yield result.sum
//   println(a.unsafeRunSync())

//   val b = for
//     ref <- Ref.of[IO, List[Int]](List.empty)
//     cast = throwDice.flatMap(result => ref.update(result :: _))
//     _ <- List(cast, cast).parSequence
//     casts <- ref.get
//   yield casts

//   println(b.unsafeRunSync())

//   val c = for
//     ref <- Ref.of[IO, List[Int]](List.empty)
//     cast = throwDice.flatMap(result => ref.update(result :: _))
//     _ <- List(cast, cast, cast).parSequence
//     casts <- ref.get
//   yield casts

//   println(c.unsafeRunSync())

//   val d = for
//     ref <- Ref.of[IO, Int](0)
//     cast = throwDice.flatMap(result =>
//       if result == 6 then ref.update(_ + 1) else IO.unit
//     )
//     _ <- List.fill(100)(cast).parSequence
//     total <- ref.get
//   yield total

//   println(d.unsafeRunSync())

//   val e = List
//     .fill(100)(IO.sleep(1.second).flatMap(_ => throwDice))
//     .parSequence
//     .map(_.sum)

//   println(e.unsafeRunSync())

//   ()

// def throwDice: IO[Int] = IO {
//   val r = scala.util.Random
//   r.nextInt(6) + 1
// }

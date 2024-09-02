//> using scala 3.5.0
//> using dep "co.fs2::fs2-core:3.11.0"

import fs2.Stream
import cats.effect.{IO, IOApp}
import fs2.Pull
import fs2.Chunk
import scala.concurrent.duration.DurationInt

object Main extends IOApp.Simple {
  def run: IO[Unit] = {
    // As is named, Pull is a data structure to represent "behaviour" when Stream requires element(s).
    // When Stream requires some elements, Pull is executed and emits elements to Stream as side effect.
    // If Stream is satisfied, further Pull is not executed. This is called "pull-based" processing.
    // Or, if Stream is not satisfied, Pull is executed again and again until Stream is satisfied.
    // When Pull is reached to the end, it emits no element and stops.
    // Pull is alike IO, but it will be executed only when Stream requires.

    val _done = Pull.done // Pull.done emits no element and terminates.

    // Pull.output1 emits a single element when Stream requires.
    // Since Pull is a monad, you can use flatMap to chain Pulls.
    val _init0 = Pull.output1(0) >> Pull.output1(1)
    // You can also use Chunk to emit multiple elements at once.
    // This is useful when you operate on block devices or network devices.
    val init = Pull.output(Chunk(0, 1))

    // Pull can output value and pass it to other ones.
    // The value outputted by Pull has nothing to do with the value emitted by Stream.
    // In contrast to Stream, the main concern of Pull is making output value.
    // Emitting value is just a side effect.
    // Can you see type signature of ioPull?
    // It's Pull[IO, Int, Unit]. It means that Pull emits Int value and returns Unit.
    val ioPull: Pull[IO, Int, Unit] = for {
      _ <- Pull.eval(IO(println("Hello, Pull!")))
      n <- Pull.eval(IO(42))
      _ <- Pull.output1(n)
    } yield ()

    // You can make a Stream from Pull.
    val ioPullStream = ioPull.stream

    // Okay, let's define a Pull to generate a stream of Fibonacci numbers.
    // We need previous two elements to generate the next element.
    // In Pull's context, we can recursively chain Pulls to emit elements.

    // This is a simple Pull to emit 0 again and again.
    // It's recursive, but it's stack safe because flatMap (>>) is defined as lazy manner.
    // When Stream is satisfied, No further Pull is evaluated.
    def go0: Pull[IO, Int, Unit] = Pull.output1(0) >> go0

    // We can pass state as arguments to Pull.
    // This is a simple Pull to emit n, n+1, n+2, ...
    def go1(curr: Int): Pull[IO, Int, Unit] =
      Pull.output1(curr) >> go1(curr + 1)

    // Okay, let's define a Pull to generate Fibonacci numbers.
    // We need state to generate the next element: previous two elements.
    // So we need to pass two arguments to Pull.
    def go(prev: Int, curr: Int): Pull[IO, Int, Unit] = {
      val next = prev + curr
      if next < 0 then Pull.done // stop when overflow
      else Pull.output1(next) >> go(curr, next)
    }

    // Thus, we can emit 0, 1 and pass the last two elements to go.
    val fibPull: Pull[IO, Int, Unit] = init >> go(0, 1)

    // Now, let's make a Stream from Pull and consume it.
    val stream = fibPull.stream
    stream
      .evalMap(i => IO(println(i)))
      .compile
      .drain
  }
}

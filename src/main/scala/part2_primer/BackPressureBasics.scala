package part2_primer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

object BackPressureBasics extends App{

  implicit val system = ActorSystem("BackPressureBasics")
  implicit val materializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int]{ x =>
    // simulate a long processing
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

  // fastSource.to(slowSink).run()
  // not back pressure

  // fastSource.async.to(slowSink).run()
  // back pressure because there has to be some mechanism to slow down one actor and communicate with the other actor

  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming: $x")
    x + 1
  }

  fastSource.async
    .via(simpleFlow).async
    .to(slowSink)
  //   .run() // works here as well

  /*
      reactions to back pressure (in order):
      - try to slow down if possible (not possible in flows as it cant control its input)
      - buffer elements until there's more demand
      - drop down elements from the buffer if it overflows
      - tear down/kill the whole stream (failure)
   */

  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)
  fastSource.async
    .via(bufferedFlow).async
    .to(slowSink) // first 16 are buffered at the sink and from 991-1000 are buffered because they are the newest
    .run()

    /*
    1-16: nobody is back pressured
    17-26: flow will buffer, flow will start dropping at the next element
    26-1000: flow will always drop the oldest element
      => 991-1000 => 992 - 1001 => sink
     */

  /*
  overflow strategies:
  - drop head = oldest
  - drop tail = newest
  - drop new = exact element to be added = keeps the buffer
  - back pressure signal
  - fail
   */

  // manual triggering back pressure
  import scala.concurrent.duration._
  fastSource.throttle(2,1 second).runWith(Sink.foreach(println))


}

package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip, Balance}

object GraphBasics extends App{

  implicit val system = ActorSystem("GraphBasics")
  implicit val materializer = ActorMaterializer()

  val input = Source(1 to 1000)
  val incrementer = Flow[Int].map(x => x + 1) // hard computation
  val multiplier = Flow[Int].map(x => x * 10) // hard computation
  val output = Sink.foreach[(Int, Int)](println)

  // step 1 - setting up the fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] => // builder = MUTABLE data structure
      import GraphDSL.Implicits._ // brings some nice operators in scope


      // step 2 - add the necessary components of this graph
      val broadcast = builder.add(Broadcast[Int](2)) // fan-out operator
      val zip = builder.add(Zip[Int, Int]) // fan-in operator

      // step 3 - tying up the components
      input ~> broadcast // input feeds into the broadcast

      broadcast.out(0) ~> incrementer ~> zip.in0
      broadcast.out(1) ~> multiplier ~> zip.in1

      zip.out ~> output

      // step 4 - return a closed shape
      ClosedShape // FREEZE the builder's shape
      // shape
    }// graph
  )// runnable graph

   //graph.run() // run the graph and materialize it

  /**
    * exercise 1: feed a source into 2 sinks at the same time (hint: use a broadcast)
    */

  // step 1
  val firstSink = Sink.foreach[Int](x => println(s"First sink: $x"))
  val secondSink = Sink.foreach[Int](x => println(s"Second sink: $x"))

    val sourceToTwoSinksGraph = RunnableGraph.fromGraph(
      GraphDSL.create(){ implicit builder =>
        import GraphDSL.Implicits._

        // step 2 - declaring components
        val broadcast = builder.add(Broadcast[Int](2))

        // step 3 - tying up the components
        input ~> broadcast ~> firstSink // implicit port numbering
                 broadcast ~> secondSink
        // or
//        broadcast.out(0) ~> firstSink
//        broadcast.out(1) ~> secondSink

        // step 4
        ClosedShape
      }
    )

  // sourceToTwoSinksGraph.run()

  import scala.concurrent.duration._
  val fastSource = input.throttle(5, 1 second)
  val slowSource = input.throttle(2, 1 second)

  val balanceGraph = RunnableGraph.fromGraph(
    GraphDSL.create(){ implicit builder =>
      import GraphDSL.Implicits._

      // step 2 - declaring components
      val merge = builder.add(Merge[Int](2))
      val balance = builder.add(Balance[Int](2))

      // step 3 - tying up the components
      fastSource ~> merge ~> balance ~> firstSink
      slowSource ~> merge;   balance ~> secondSink
      // or
      //        broadcast.out(0) ~> firstSink
      //        broadcast.out(1) ~> secondSink

      // step 4
      ClosedShape
    }
  )

  balanceGraph.run() // this helps in merging 2 unknown sources of varying speeds and then balance them out among the sinks
}

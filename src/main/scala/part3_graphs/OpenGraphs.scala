package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source}

object OpenGraphs extends App{

  implicit val system = ActorSystem("OpenGraphs")
  implicit val materializer = ActorMaterializer()

  /*
  A composite source that concatenates 2 sources
  - emits all the elements from the first source
  - then all the elements from the second
   */

  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 1000)



  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] => // builder = MUTABLE data structure
      import GraphDSL.Implicits._ // brings some nice operators in scope
      // brings some nice operators in scope


      // step 2 - add the necessary components of this graph
      val concat = builder.add(Concat[Int](2))

      // step 3 - tying up the components
      firstSource ~> concat
      secondSource ~> concat

      // step 4 - return a closed shape
      SourceShape(concat.out)// FREEZE the builder's shape
      // shape
    }// graph
  )// runnable graph

  // sourceGraph.to(Sink.foreach(println)).run()

  /*
  Complex sink
   */

  val sink1 = Sink.foreach[Int](x => println(s"Meaningful thing 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Meaningful thing 2: $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._


      val broadcast = builder.add(Broadcast[Int](2))

      broadcast ~> sink1
      broadcast ~> sink2

      SinkShape(broadcast.in)
    }
  )

  // firstSource.to(sinkGraph).run()

  /**
    * Challenge - complex flow?
    * Write your own flow that's composed of two other flows
    * - one that adds 1 to a number
    * - one that does number * 10
    */

  val incrementer = Flow[Int].map(_+1)
  val multiplier = Flow[Int].map(_*10)

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      // everything operates on shapes not components
      val incrementerShape = builder.add(incrementer)
      val multiplierShape = builder.add(multiplier)

      incrementerShape ~> multiplierShape

     FlowShape(incrementerShape.in, multiplierShape.out) // SHAPE
    } // static graph
  ) // component

  firstSource.via(flowGraph).to(Sink.foreach(println)).run()

  /**
    * Exercise: flow from a sink and a source?
    */

  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] =
    Flow.fromGraph(
      GraphDSL.create(){ implicit builder =>

        val sourceShape = builder.add(source)
        val sinkShape = builder.add(sink)

        FlowShape(sinkShape.in, sourceShape.out)
      }
    )

  val f = Flow.fromSinkAndSourceCoupled(Sink.foreach[String](println), Source(1 to 10)) // but no way to stop or apply back pressure hence another method is available (Coupled)

}

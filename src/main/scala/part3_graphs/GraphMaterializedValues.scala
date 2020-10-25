package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, SinkShape}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App{

  implicit val system = ActorSystem("GraphMaterializedValues")
  implicit val materializer = ActorMaterializer()

  val wordSource = Source(List("Akka", "is", "awesome", "rock", "the", "jvm"))
  val printer = Sink.foreach[String](println)
  val counter = Sink.fold[Int, String](0)((count, _) => count + 1)

  /*
  A composite component (sink)
  - prints out all strings which are lower case
  - COUNTS the strings that are short(< 5 chars)
   */

  val complexWordSink = Sink.fromGraph(
    GraphDSL.create(counter) { implicit builder => counterShape => // created a materialized value of the parameter added
    import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[String](2))
      val lowercaseFilter = builder.add(Flow[String].filter(word => word == word.toLowerCase))
      val shortStringFilter = builder.add(Flow[String].filter(_.length < 5))

      broadcast ~> lowercaseFilter ~> printer
      broadcast ~> shortStringFilter ~> counterShape

      SinkShape(broadcast.in)
    }
  )
 import system.dispatcher
  val shortStringsCountFuture = wordSource.toMat(complexWordSink)(Keep.right).run()
  shortStringsCountFuture onComplete {
    case Success(count) => println(s"The total number of short strings is: $count")
    case Failure(exception) => println(s"The count of short strings failed: $exception")
  }

  /**
    * Exercise
    */

  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val counterSink = Sink.fold[Int, B](0)((count, _) => count+1)
    Flow.fromGraph(
      GraphDSL.create(counterSink){ implicit builder => counterSinkShape =>
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[B](2))
        val originalFlowShape = builder.add(flow)

        originalFlowShape ~> broadcast ~> counterSinkShape

        FlowShape(originalFlowShape.in, broadcast.out(1))
      }
    )
  }

  val simpleSource = Source(1 to 42)
  val simpleFlow = Flow[Int].map(x => x)
  val simpleSink = Sink.ignore

  val enhancedFlowCountFuture = simpleSource.viaMat(enhanceFlow(simpleFlow))(Keep.right).toMat(simpleSink)(Keep.left).run()

  enhancedFlowCountFuture onComplete{
    case Success(count) => println(s"$count elements went through the enhanced flow")
    case Failure(exception) => println("Something went wrong")
  }
  /*
  Hint: use a broadcast and a Sink.fold
   */
}

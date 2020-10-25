package playground

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future


object Playground extends App{

  implicit val actor = ActorSystem("Playground")
  implicit val materializer =  ActorMaterializer()

  val source = Source(List("a", "b", "c"))
  val sink = Sink.fold[String, String]("")(_ + _)

  val runnable: RunnableGraph[Future[String]] = source.toMat(sink)(Keep.right)
  val result: Future[String] = runnable.run()

  // Source.single("hello, streams!").to(Sink.foreach(println)).run()

}

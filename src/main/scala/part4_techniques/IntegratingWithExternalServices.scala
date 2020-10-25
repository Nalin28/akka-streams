package part4_techniques

import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import scala.concurrent.Future

object IntegratingWithExternalServices extends App{

  implicit val system = ActorSystem("IntegratingWithExternalServices")
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")
  //import system.dispatcher // not recommended in practice with mapAsync because it might starve the threads (since Futures will be using them as well)

  def genericExtService[A, B](element: A): Future[B] = ???

  // example: simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)

  val eventSource = Source(List(
    PagerEvent("AkkaInfra", "Infrastructure broke", new Date),
    PagerEvent("FastDataPipeline", "Illegal elements in the data pipeline", new Date),
    PagerEvent("AkkaInfra", "A services stopped responding", new Date),
    PagerEvent("SuperFrontend", "A button doesn't work", new Date)
  ))

  object PagerService {
    private val engineers = List("Daniel", "John", "Lady Gaga")
    private val emails = Map(
      "Daniel" -> "daniel@rockthejvm.com",
      "John" -> "john@rockthejvm.com",
      "Lady Gaga" -> "ladygaga@rtjvm.com"
    )

    // exposing the api (method)
    def processEvent(pagerEvent: PagerEvent) = Future {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24*3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      println(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)

      // return the email that was paged
      engineerEmail
    }
  }

  val infraEvents = eventSource.filter(_.application == "AkkaInfra")
  val pagedEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => PagerService.processEvent(event)) // useful for flows that call external services that return Future
  // guarantees the relative order of elements
  val pagedEmailSink = Sink.foreach[String](email => println(s"Successfully sent notification to $email"))

 // pagedEngineerEmails.to(pagedEmailSink).run()

  // using Actors

  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("Daniel", "John", "Lady Gaga")
    private val emails = Map(
      "Daniel" -> "daniel@rockthejvm.com",
      "John" -> "john@rockthejvm.com",
      "Lady Gaga" -> "ladygaga@rtjvm.com"
    )

    // exposing the api (method)
    private def processEvent(pagerEvent: PagerEvent) = {
      val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(engineerIndex.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      log.info(s"Sending engineer $engineerEmail a high priority notification: $pagerEvent")
      Thread.sleep(1000)

      // return the email that was paged
      engineerEmail
    }

    override def receive: Receive = {
      case pagerEvent: PagerEvent =>
        sender ! processEvent(pagerEvent)
    }
  }
  import akka.pattern.ask
  import scala.concurrent.duration._
  implicit val timeout = Timeout(4 seconds)
  val pagerActor = system.actorOf(Props[PagerActor], "pagerActor")
  val alternativePagedEngineerEmails = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])
  alternativePagedEngineerEmails.to(pagedEmailSink).run()

  // do not confuse mapAsync with async (ASYNC boundary)
}

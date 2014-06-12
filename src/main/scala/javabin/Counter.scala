package javabin

import akka.actor.{Props, ActorSystem, Actor}
import akka.actor.Actor.Receive
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await

case object Get
case object IncrementAndGet

class Counter extends Actor {
  var counter = 0
  override def receive = {
    case Get => sender ! counter
    case IncrementAndGet =>
      counter += 1
      sender ! counter
  }
}

object StatefulMain extends App {
  val system = ActorSystem("state")
  val counter = system.actorOf(Props[Counter], "counter")

  implicit val timeout = Timeout(1 second)
  implicit val executor = system.dispatcher

   for (
    a <- counter ? Get;
    b <- counter ? IncrementAndGet;
    c <- counter ? IncrementAndGet;
    d <- counter ? Get
  ) println(s"$a $b $c $d")
}

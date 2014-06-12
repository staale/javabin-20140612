package javabin

import akka.actor.{Props, Actor, ActorLogging, ActorSystem}
import akka.actor.Actor.Receive

object Main extends App {
  val system = ActorSystem("HelloWorld")
  val printerRef = system.actorOf(Props[Printer], "printer")
  printerRef ! Msg("Hello world")
  Thread.sleep(10000)
  system.shutdown()
}

case class Msg(content: String)

class Printer extends Actor with ActorLogging {
  override def receive = {
    case Msg(content) => log.info("Got message: {}", content)
  }
}
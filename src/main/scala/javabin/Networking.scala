package javabin

import akka.actor._
import com.typesafe.config.ConfigFactory

import akka.actor.ActorIdentity
import org.joda.time.DateTime
import concurrent.duration._

/**
 * Created by staaleu on 3/6/14.
 */
object ConfigHelper {
  def withPort(port: Int) = s"""
      |akka {
      |  actor.provider = "akka.remote.RemoteActorRefProvider"
      |  remote.netty.tcp.port = $port
      |  remote.netty.tcp.hostname = localhost
      |}
    """.stripMargin

}

object ServerMain extends App {
  val config = ConfigHelper.withPort(2550)
  val system = ActorSystem("server", ConfigFactory.parseString(config))

  val serverRef = system.actorOf(Props[Server], "server")
}

class Server extends Actor with ActorLogging {

  var messageCount = 0

  override def receive = {
    case msg =>
      messageCount += 1
      log.info("{}: Got message [{}] from [{}]", messageCount, msg, sender)
      if (messageCount >= 10) {
        log.info("Shutting down after {} messages", messageCount)
        context.stop(self)
        val sys = context.system
        context.system.scheduler.scheduleOnce(10 seconds, new Runnable {
          override def run() {
            sys.shutdown()
          }
        })(context.dispatcher)
      }
  }
}

object ClientMain extends App {
  val config = ConfigHelper.withPort(2551)
  val system = ActorSystem("client", ConfigFactory.parseString(config))

  val server = system.actorSelection("akka.tcp://server@localhost:2550/user/server")

  val clientRef = system.actorOf(Props(classOf[Client], server), "client")
}

class Client(partner: ActorSelection) extends Actor with ActorLogging with Stash {

  partner ! Identify(1)

  context.system.scheduler.schedule(1 second, 1 second, self, "transmit")(context.dispatcher)

  override def receive = {
    case ActorIdentity(1, Some(ref)) =>
      log.info("Watching partner {}", ref)
      context.watch(ref)
      
    case "transmit" => 
      partner ! "Time is " + DateTime.now()
      
    case Terminated(ref) =>
      log.info("Partner at {} terminated, shuting down", ref)
      context.system.shutdown()
  }
}
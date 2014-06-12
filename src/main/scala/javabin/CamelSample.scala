package javabin

import ch.qos.logback.classic.Level
import akka.actor.{Props, ActorSystem, ActorLogging, Actor}
import akka.camel.{CamelMessage, Consumer}
import com.sun.syndication.feed.synd.{SyndEntry, SyndFeed}
import collection.JavaConversions._
import org.slf4j.LoggerFactory

/**
 * Created by staaleu on 4/6/14.
 */
object CamelSample extends App {
  // Change log level for org.apache to info in slf4j
  LoggerFactory.getLogger("org.apache").asInstanceOf[ch.qos.logback.classic.Logger].setLevel(Level.INFO)
  
  val system = ActorSystem("camelSample")

  system.actorOf(Props[RssReader], "rss")

}

class RssReader extends Actor
  // Mix in the Camel consumer, to consume messages from a camel uri
  with Consumer
  // Mix in actor logging to be able to log messages
  with ActorLogging {

  // The endpointUri is any valid camel endpoint
  override def endpointUri = "rss:http://www.vg.no/rss/feed/forsiden/?consumer.delay=1000&sortEntries=true"

  override def receive = {
    case CamelMessage(feed:SyndFeed, headers) =>
      // Get the entries from the SyndFeed, convert to scala, and cast to List[SyndEntry]
      val entries = feed.getEntries.toList.asInstanceOf[List[SyndEntry]]

      for (entry <- entries) {
        log.info(s"${entry.getPublishedDate}: ${entry.getTitle} - ${entry.getUri}")
      }
  }
}

package javabin

import akka.actor._
import com.typesafe.config.ConfigFactory
import akka.cluster.Cluster
import akka.contrib.pattern.{ClusterSingletonProxy, ClusterSingletonManager}
import scala.Some
import scala.util.Random
import akka.actor.Actor.Receive
import scala.concurrent.Await
import akka.util.Timeout
import concurrent.duration._
import akka.pattern.ask
import org.joda.time.DateTime


/**
 * Created by staaleu on 3/6/14.
 */
object ClusterSample extends App {
  // Configuration for clustering. The only needed part is the
  // actor provider, and the port. We set it to 0 to force a random
  // port.

  // In a normal config, we would probably have some seed nodes to set the initial
  // members of the cluster.

  // Everything under cluster here is just to make timeouts very short, so a new master
  // is elected quickly for cluster singleton
  val config =
    """
      |akka {
      |  loglevel = "OFF"
      |  actor.provider = "akka.cluster.ClusterActorRefProvider"
      |  remote.netty.tcp.port = 0
      |
      |  cluster {
      |    auto-down-unreachable-after = 50 ms
      |    gossip-interval = 50 ms
      |    unreachable-nodes-reaper-interval = 100 ms
      |    leader-actions-interval = 100 ms
      |
      |    failure-detector {
      |      heartbeat-interval = 250 ms
      |      threshold = 2.0
      |      acceptable-heartbeat-pause = 250 ms
      |      max-sample-size = 100
      |    }
      |  }
      |}
    """.stripMargin

  // Create a list of 10 actor systems
  val nodes = List.fill(10)(ActorSystem("cluster", ConfigFactory.parseString(config)))

  // Implicits needed for doing waiting on response from actors.
  implicit val dispatch = nodes.last.dispatcher
  implicit val timeout = Timeout(10 second)

  // For each node, join all the nodes as cluster seed nodes.
  // Even a cluster of 1 node need to join itself to start the cluster.
  for (node <- nodes) {
    Cluster(node).joinSeedNodes(nodes.map(Cluster(_).selfAddress).toSeq)
  }

  // Set up the cluster singleton on each node
  for (node <- nodes) {
    // The manage is responsible for creating the singleton on the oldest
    // system in the cluster. The singletonProps is the configuration for the singleton.
    node.actorOf(ClusterSingletonManager.props(
      singletonProps = Props[Singleton],
      singletonName = "consumer",
      terminationMessage = PoisonPill,
      role = None),
      name = "singleton")

    // The proxy is responsible for discovering where the singleton is located,
    // and to forward all messages to the singleton on that system. The singletonPath
    // is the path to the manager + name of the singleton
    val proxy = node.actorOf(ClusterSingletonProxy.props(
      singletonPath = "/user/singleton/consumer",
      role = None),
      name = "consumerProxy")

    // Ask the singleton for it's identity
    println(s"${DateTime.now()} Singleton address is: " + Await.result(proxy ? "identify", 10 seconds))
  }

  // Shuffle all but the last node, and iterate
  for (node <- Random.shuffle(nodes.init)) {
    // Check the current address of the singleton
    println(s"${DateTime.now()} Singleton address is: " + Await.result(node.actorSelection("/user/consumerProxy") ? "identify", 10 seconds))
    // Shut down the node. If the singleton is on this node, this will cause a new leader
    // election, and the singleton to be created on a new system.
    println(s"${DateTime.now()} Shutting down ${Cluster(node).selfAddress}")
    node.shutdown()
    println()
    // wait for leader election to happen
    Thread.sleep(7000)
  }

  // Shut down the last node to end the process
  println(s"${DateTime.now()} shutting down last node")
  nodes.last.shutdown()
}

class Singleton extends Actor with ActorLogging {
  // Print when we are constructed the address of the node we are constructed on.
  println(s"${DateTime.now()} Constructed at " + Cluster(context.system).selfAddress)

  override def receive = {
    case "identify" => sender ! self
  }
}

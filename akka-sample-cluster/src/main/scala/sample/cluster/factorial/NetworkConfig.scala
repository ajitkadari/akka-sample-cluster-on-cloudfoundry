package sample.cluster.factorial

import java.net.{InetAddress, NetworkInterface, URLEncoder}

import scala.collection.JavaConversions._
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}
import scalaj.http.{Http, HttpOptions, HttpResponse}

/**
  * Created by Admin on 2016-08-31.
  */
object NetworkConfig {

  def hostLocalAddress: String =
    NetworkInterface.getNetworkInterfaces.
        find(_.getName equals "eth0").
        flatMap(interface =>
          interface.getInetAddresses.find(_.isSiteLocalAddress).map(_.getHostAddress)).
        getOrElse("127.0.0.1")

  def serviceInstanceIp = Option(System.getenv("CF_INSTANCE_IP"))

// Amalgam8 Registry's full URL
  def registryBaseUrl = System.getenv("REGISTRY_BASE_URL")
  def registryApiLink: String = "/api/v1/instances"
  def registryParameter: String = "?service_name=cluster-seed"
  def registryHeartBeat: String = "/heartbeat"

  def seedNodesIps: Seq[String] = Option(System.getenv("SEED_DISCOVERY_SERVICE")).
      map(InetAddress.getAllByName(_).map(_.getHostAddress).toSeq).
      getOrElse(Seq.empty)

  def cloudFoundryIp2 = {
    InetAddress.getAllByName("localhost").map(_.getHostAddress).toSeq
      .collectFirst({case ip if ip.startsWith("10.255.") => ip})
  }

  def seedNodesPorts: Seq[String] = Option(System.getenv("SEED_PORT")).
    map(port => Seq.fill(seedNodesIps.size)(port)).getOrElse(Seq.empty)

  def seedsConfig(
                   config: Config,
                   clusterName: String,
                   defaultIp: String,
                   defaultPort: Int): Config =
    if(!seedNodesIps.isEmpty)
      ConfigFactory.empty().withValue("akka.cluster.seed-nodes",
        ConfigValueFactory.fromIterable(seedNodesIps.zip(seedNodesPorts).
          map{case (ip, port) => s"akka.tcp://$clusterName@$ip:$port"}))
    else {
      val backendInstances = queryServiceInstances match {
        case l if l.size >= 2 => l
        case l => l ++ Seq(s"$defaultIp:$defaultPort")
      }
      // if(!backendInstances.isEmpty) { //TODO: fix local setup
        println(s"backends: $backendInstances")
        ConfigFactory.empty().withValue("akka.cluster.seed-nodes",
          ConfigValueFactory.fromIterable(backendInstances.map(ipPort => s"akka.tcp://$clusterName@$ipPort")))
      // } else ConfigFactory.empty()
    }

  def queryServiceInstances = {
    val resp = Http(registryBaseUrl + registryApiLink + registryParameter)
      .option(HttpOptions.readTimeout(10000)).asString
    (Json.parse(resp.body) \\ "value").map(_.as[String])
  }

  def registerService(ip: String, port: Int)(implicit context: ExecutionContext): Future[String] = {
    Future(Http(registryBaseUrl + registryApiLink)
      .postData(
        s"""{
          |"service_name": "cluster-seed",
          |"endpoint": {"type": "tcp","value": "$ip:$port"},
          |"ttl": 30,
          |"status": "UP",
          |"tags": ["tag-test"],
          |"metadata": {}}""".stripMargin)
      .header("Content-Type", "application/json")
      .header("Charset", "UTF-8")
      .option(HttpOptions.readTimeout(10000)).asString)
      .map(resp => (Json.parse(resp.body) \ "id").as[String])
  }

  def heartbeat(id: String)(implicit context: ExecutionContext): Future[String] = {
    Future(Http(registryBaseUrl + registryApiLink + "/" + id + registryHeartBeat)
      .put("")
      .header("Content-Type", "application/json")
      .header("Charset", "UTF-8")
      .option(HttpOptions.readTimeout(10000)).asString)
      .map(_.body)
  }
}

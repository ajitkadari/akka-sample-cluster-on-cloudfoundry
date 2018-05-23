This repo contains sample Akka Cluster app integrated with Amalgam8's Registry.

# How to run Akka Cluster on Pivotal Cloud Foundry (PCF)

This is a short guide to walk you through how to deploy and run Akka Cluster based application on [Pivotal Cloud Foundry (PCF)](https://pivotal.io/platform)

**Note:** Akka with no Remoting / Cluster can run on PCF with no additional requirement. This article deals with cases when Remoting / Cluster features are used.

**Background:** [Akka Cluster](http://doc.akka.io/docs/akka/snapshot/scala/cluster-usage.html) is based on TCP communication or optionally can use UDP instead in Akka version >= 2.4.11. PCF's standard container-to-container (C2C) mechanism allows apps talking to other apps via TCP or UDP; however, the ingress traffic to the entry point application supports only HTTP(S) and TCP.

In this guide we will use this PCF C2C feature to show how to run Akka Cluster that uses TCP. 

**TO DO:** In case of UDP, setting `canonical.port` ([2.4.11 release notes](http://akka.io/news/2016/09/30/akka-2.4.11-released.html)) will make a UDP-based Akka Cluster usable on PCF as well.

## Prerequisites
The following instructions for this example assume the following:
- [This git repo](https://github.com/gtantachuco-pivotal/akka-sample-cluster-on-cloudfoundry/edit/master/README.md) cloned somewhere
- [The CF Networking Release git repo](https://github.com/gtantachuco-pivotal/cf-networking-release) cloned somewhere
- [jq](https://stedolan.github.io/jq/download/)
- A Pivotal Cloud Foundry  foundation: 1.10 or higher

## Why do we need Amalgam8's Registry?
With Akka Clusters, every node should know IPs/hostnames and ports of [cluster seed nodes](http://doc.akka.io/docs/akka/current/scala/cluster-usage.html#Joining_to_Seed_Nodes). Containers in PCF have dynamic IPs making it difficult to manage a list of static IPs for seed nodes. One possible way to bootstrap a cluster is when the first node joins itself and publishes its IP in a Shared Registry that is accessible to the rest of nodes. More nodes can register themselves as seed nodes later. Solutions include using [etcd](https://github.com/coreos/etcd) directly or via [ConstructR](https://github.com/hseeberger/constructr) that utilizes `etcd` as Akka extension. We used [amalgam8](https://github.com/amalgam8/amalgam8/tree/master/registry) because you can deploy it as a Go app in PCF. 
**NOTE:** While it works for proof-of-concept implementation, amalgam8 can not be used in production "as is" since simultaneous seed nodes registration with amalgam8 has high chances of forming multiple separated cluster.

## Deploy Amalgam8's Registry to PCF



## Deploying Akka application

You can deploy Akka application by using your foundation's [java-buildpack](https://github.com/cloudfoundry/java-buildpack.git). Our sample application is inspired by [akka-sample-cluster](https://github.com/akka/akka/tree/master/akka-samples/akka-sample-cluster-scala)). It has backend nodes that calculate factorial upon receiving messages from frontend nodes. Frontend nodes also expose HTTP interface `GET <frontend-hostname>/info` that shows number of jobs completed.


- Clone sample application: from [here](https://github.com/katrinsharp/akka-sample-cluster-on-cloudfoundry).
- Compile and package sample components:
```
cd akka-sample-cluster
sbt backend:assembly # backend
sbt frontend:assembly # frontend
```
- Deploy amalgam8: amalgam8 installation along with a sample application can be found [here](https://github.com/cloudfoundry-incubator/netman-release/tree/develop/src/example-apps/tick).
- Deploy sample Akka backend: with `--no-route` and `--health-check-type none` options since backend doesn't expose any HTTP ports: 
```
cf push --no-route --health-check-type none sample-akka-cluster-backend -p target/scala-2.11/akka-sample-backend.jar -b https://github.com/cloudfoundry/java-buildpack.git
cf access-allow sample-akka-cluster-backend sample-akka-cluster-backend --port 2551 --protocol tcp
cf logs sample-akka-cluster-backend # check the log to see that first node joined itself
cf scale sample-akka-cluster-backend -i 2 # needed since our sample application requires 2 backends, can be changed configuration
```

**Note:** to prevent cluster split, verify that the first node is running before scaling it. 

- Deploy sample Akka frontend:
```
cf push sample-akka-cluster-frontend -p target/scala-2.11/akka-sample-frontend.jar -b https://github.com/cloudfoundry/java-buildpack.git
cf access-allow sample-akka-cluster-frontend sample-akka-cluster-backend --port 2551 --protocol tcp
```
- Verify that it works:
```
curl sample-akka-cluster-frontend.bosh-lite.com/info #it should show the number of completed jobs
```

## Summary

This guide shows a prototype implementation so it requires more than that to have a production Akka Cluster on Cloud Foundry, however it is a successful proof-of-concept that demonstrates Akka Cluster working with the latest Cloud Foundry release and the new experimental network plugin.

This repo contains sample Akka Cluster app integrated with amalgam8.

# How to run Akka Cluster on CloudFoundry

This is a short guide to walk you through how to deploy and run Akka Cluster based application on [CloudFoundry](https://www.cloudfoundry.org/)

**Note:** Akka with no Remoting / Cluster can run on Cloud Foundry with no additional requirement. This article deals with cases when Remoting / Cluster features are used.

**Background:** [Akka Cluster](http://doc.akka.io/docs/akka/snapshot/scala/cluster-usage.html) is based on TCP communication or optionally can use UDP instead in Akka version >= 2.4.11. However, the current CloudFoundry release [v245](https://github.com/cloudfoundry/cf-release/releases/tag/v245) doesn't support container-to-container TCP or UDP based communication. The develop branch of [Container Networking for Garden-RunC](https://github.com/cloudfoundry-incubator/netman-release) plugin contains support for this type of communication on CloudFoundry with fixed port numbers (Pivotal plans to support port number ranges in the future). In this guide we will use this experimential plugin to show how to run Akka Cluster that uses TCP. In case of UDP, setting `canonical.port` ([2.4.11 release notes](http://akka.io/news/2016/09/30/akka-2.4.11-released.html)) will make UDP based Akka Cluster usable on CloudFoundry as well.


**Note:** For this guide we installed Cloud Foundry **locally** using [bosh-lite](https://github.com/cloudfoundry-incubator/netman-release/blob/develop/README.md#deploy-to-bosh-lite). CI deployment scripts and config for AWS provided by CloudFoundry can be found [here](https://github.com/cloudfoundry-incubator/container-networking-ci).


**Installing CloudFoundry components:**

Both development environment for BOSH and container networking plugin are works in progress, thus we advise to follow their respective latest documentation for installation instructions. In our case we used develop branch as of September 28th, 2016.

**Enabling container-to-container networking**

By default, there is no network access between different containers. CloudFoundry provides a sample application [Cats-and-Dogs](https://github.com/cloudfoundry-incubator/netman-release/tree/master/src/example-apps/cats-and-dogs) that contains instructions how to enable the access on a per port basis.

**Deploying Akka application** 

You can deploy Akka application by using [java-buildpack](https://github.com/cloudfoundry/java-buildpack.git). Our sample application is inspired by [akka-sample-cluster](https://github.com/akka/akka/tree/master/akka-samples/akka-sample-cluster-scala)). It has backend nodes that calculate factorial upon receiving messages from frontend nodes. Frontend nodes also expose HTTP interface `GET <frontend-hostname>/info` that shows number of jobs completed.

**Background:** with Akka Cluster every node should know IPs/hostnames and ports of [cluster seed nodes](http://doc.akka.io/docs/akka/current/scala/cluster-usage.html#Joining_to_Seed_Nodes). Containers in Cloud Foundry have dynamic IPs making it impossible to manage a list of static IPs for seed nodes. One possible way to bootstrap a cluster is when the first node joins itself and publishes its IP in some sort of shared registry that is accessible to the rest of nodes. More nodes can register themselves as seed nodes later. Cloud Foundry doesn't have any type of Service Registry built-in. Some of the solutions could be to use [etcd](https://github.com/coreos/etcd) directly or via [ConstructR](https://github.com/hseeberger/constructr) that utilizes etcd as Akka extension. We used [amalgam8](https://github.com/amalgam8/amalgam8/tree/master/registry) because Cloud Foundry provides an easy way to install amalgam8 on bosh-lite. While it works for proof-of-concept implementation, amalgam8 can not be used in production as is since simultaneous seed nodes registration with amalgam8 has high chances of forming multiple separated cluster.

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

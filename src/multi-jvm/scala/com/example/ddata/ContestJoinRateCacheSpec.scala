package com.example.ddata

import scala.concurrent.duration._
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.ddata.Replicator
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.GetReplicaCount
import akka.cluster.ddata.typed.scaladsl.Replicator.ReplicaCount
import akka.cluster.typed.{Cluster, Join}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import com.example.ddata.ContestJoinRateCache.{AddToCache, CachedValue, GetCache, RemoveCache}
import com.typesafe.config.ConfigFactory


object ContestJoinRateCacheSpec extends MultiNodeConfig {
  val node1 = role("node-1")
  val node2 = role("node-2")
  val node3 = role("node-3")

  commonConfig(ConfigFactory.parseString("""
    akka.loglevel = INFO
    akka.actor.provider = "cluster"
    akka.log-dead-letters-during-shutdown = off
    """))

}

class ContestJoinRateCacheSpecMultiJvmNode1 extends ContestJoinRateCacheSpec
class ContestJoinRateCacheSpecMultiJvmNode2 extends ContestJoinRateCacheSpec
class ContestJoinRateCacheSpecMultiJvmNode3 extends ContestJoinRateCacheSpec

class ContestJoinRateCacheSpec extends MultiNodeSpec(ContestJoinRateCacheSpec) with STMultiNodeSpec {
  import ContestJoinRateCacheSpec._

  override def initialParticipants = roles.size

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped
  val cluster = Cluster(typedSystem)
  val replicatedCache = system.spawnAnonymous(ContestJoinRateCache())

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      cluster.manager ! Join(node(to).address)
    }
    enterBarrier(from.name + "-joined")
  }

  "Demo of a replicated cache" must {
    "join cluster" in within(20.seconds) {
      join(node1, node1)
      join(node2, node1)
      join(node3, node1)

      awaitAssert {
        val probe = TestProbe[ReplicaCount]()
        DistributedData(typedSystem).replicator ! GetReplicaCount(probe.ref)
        probe.expectMessage(Replicator.ReplicaCount(roles.size))
      }
      enterBarrier("after-1")
    }

    "replicate cached entry" in within(10.seconds) {
      runOn(node1) {
        replicatedCache ! AddToCache(1, 7)
      }

      awaitAssert {
        val probe = TestProbe[CachedValue]()
        replicatedCache.tell(GetCache(1, probe.ref))
        probe.expectMessage(CachedValue(1, Some(7)))
      }

      enterBarrier("after-2")
    }

    "replicate many cached entries" in within(10.seconds) {
      runOn(node1) {
        for (i <- 100 to 200)
          replicatedCache ! AddToCache(i, i)
      }

      awaitAssert {
        val probe = TestProbe[CachedValue]()
        for (i <- 100 to 200) {
          replicatedCache.tell(GetCache(i, probe.ref))
          probe.expectMessage(CachedValue(i, Some(i)))
        }
      }

      enterBarrier("after-3")
    }

    "replicate evicted entry" in within(15.seconds) {
      runOn(node1) {
        replicatedCache ! AddToCache(2, 6)
      }

      awaitAssert {
        val probe = TestProbe[CachedValue]()
        replicatedCache.tell(GetCache(2, probe.ref))
        probe.expectMessage(CachedValue(2, Some(6)))
      }
      enterBarrier("key2-replicated")

      runOn(node3) {
        replicatedCache ! RemoveCache(2)
      }

      awaitAssert {
        val probe = TestProbe[CachedValue]()
        replicatedCache.tell(GetCache(2, probe.ref))
        probe.expectMessage(CachedValue(2, None))
      }

      enterBarrier("after-4")
    }

    "replicate updated cached entry" in within(10.seconds) {
      runOn(node2) {
        replicatedCache ! AddToCache(1, 7)
        replicatedCache ! AddToCache(1, 6)
      }

      awaitAssert {
        val probe = TestProbe[CachedValue]()
        replicatedCache.tell(GetCache(1, probe.ref))
        probe.expectMessage(CachedValue(1, Some(6)))
      }

      enterBarrier("after-5")
    }

  }

}

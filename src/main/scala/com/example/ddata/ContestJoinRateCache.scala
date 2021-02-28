package com.example.ddata

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ddata.Replicator.{GetResponse, GetSuccess, NotFound, ReadLocal, UpdateResponse, WriteLocal}
import akka.cluster.ddata.{LWWMap, LWWMapKey, SelfUniqueAddress}
import akka.cluster.ddata.typed.scaladsl.DistributedData
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, Update}

object ContestJoinRateCache {

  type RateLimitRef = ActorRef[ContestJoinRateCache.JoinRateCommand]

  sealed trait JoinRateCommand
  final case class AddToCache(key: Int, value: Int) extends JoinRateCommand
  final case class GetCache(key: Int, replyTo: ActorRef[CachedValue]) extends JoinRateCommand
  final case class CachedValue(key: Int, value: Option[Int])
  final case class RemoveCache(key: Int) extends JoinRateCommand

  private sealed trait InternalCommand extends JoinRateCommand
  private case class InternalGetResponse(key: Int, replyTo: ActorRef[CachedValue], rsp: GetResponse[LWWMap[Int, Int]])
    extends InternalCommand
  private case class InternalUpdateResponse(rsp: UpdateResponse[LWWMap[Int, Int]]) extends InternalCommand

  def apply(): Behavior[JoinRateCommand] = Behaviors.setup { context =>
    DistributedData.withReplicatorMessageAdapter[JoinRateCommand, LWWMap[Int, Int]] { replicator =>

      implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

      def dataKey(entryKey: Int): LWWMapKey[Int, Int] = LWWMapKey("cache-" + math.abs(entryKey.hashCode % 100))

      Behaviors.receiveMessage[JoinRateCommand] {
        case AddToCache(key, value) =>
          replicator.askUpdate(
            askReplyTo => Update(dataKey(key), LWWMap.empty[Int, Int], WriteLocal, askReplyTo)(_ :+ (key -> value)),
            InternalUpdateResponse.apply)

          Behaviors.same

        case RemoveCache(key) =>
          replicator.askUpdate(
            askReplyTo => Update(dataKey(key), LWWMap.empty[Int, Int], WriteLocal, askReplyTo)(_.remove(node, key)),
            InternalUpdateResponse.apply)

          Behaviors.same

        case GetCache(key, replyTo) =>
          replicator.askGet(
            askReplyTo => Get(dataKey(key), ReadLocal, askReplyTo),
            rsp => InternalGetResponse(key, replyTo, rsp))

          Behaviors.same

        case InternalGetResponse(key, replyTo, g @ GetSuccess(_, _)) =>
          replyTo ! CachedValue(key, g.dataValue.get(key))
          Behaviors.same

        case InternalGetResponse(key, replyTo, _: NotFound[_]) =>
          replyTo ! CachedValue(key, None)
          Behaviors.same

        case _: InternalGetResponse    => Behaviors.same // ok
        case _: InternalUpdateResponse => Behaviors.same // ok
      }
    }
  }
}

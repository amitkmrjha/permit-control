package com.example.ddata

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.ddata.Replicator.{GetResponse, GetSuccess, NotFound, ReadLocal, UpdateResponse, WriteLocal}
import akka.cluster.ddata.{LWWMap, LWWMapKey, SelfUniqueAddress}
import akka.cluster.ddata.typed.scaladsl.{DistributedData, Replicator, ReplicatorMessageAdapter}
import akka.cluster.ddata.Replicator._
import akka.cluster.ddata.typed.scaladsl.Replicator.{Get, Update}
import com.example.domain.{BackPressureRemoveEvent, BackPressureUpdateEvent, ContestCommand, RateLimitBackPressure}

object ContestJoinRateCache {

  type RateLimitRef = ActorRef[ContestJoinRateCache.JoinRateCommand]

  sealed trait JoinRateCommand
  final case class AddToCache(key: Int, value: Int) extends JoinRateCommand
  final case class GetCache(key: Int, replyTo: ActorRef[CachedValue]) extends JoinRateCommand
  final case class CachedValue(key: Int, value: Option[Int])
  final case class RemoveCache(key: Int) extends JoinRateCommand
  final case class RegisterSubscriberActor(ref : ActorRef[ContestCommand]) extends JoinRateCommand

  private sealed trait InternalCommand extends JoinRateCommand
  private case class InternalSubscribeResponse(rsp: SubscribeResponse[LWWMap[Int, Int]]) extends InternalCommand
  private case class InternalGetResponse(key: Int, replyTo: ActorRef[CachedValue], rsp: GetResponse[LWWMap[Int, Int]])
    extends InternalCommand
  private case class InternalUpdateResponse(rsp: UpdateResponse[LWWMap[Int, Int]]) extends InternalCommand

  def apply(): Behavior[JoinRateCommand] = Behaviors.setup { context =>
    DistributedData.withReplicatorMessageAdapter[JoinRateCommand, LWWMap[Int, Int]] { replicator =>
      initContestJoinRateCache(context,replicator)
    }
  }

  private def initContestJoinRateCache(context: ActorContext[JoinRateCommand],
                                       replicator:ReplicatorMessageAdapter[JoinRateCommand, LWWMap[Int, Int]],
                                       subscriberActorRef:Option[ActorRef[ContestCommand]] = None):Behavior[JoinRateCommand] = {
    implicit val ec = context.executionContext

    implicit val node: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress

    val RateLimitMapKey = LWWMapKey[Int, Int]("rateLimitMapKey")

    replicator.subscribe(RateLimitMapKey, InternalSubscribeResponse.apply)

    Behaviors.receiveMessage[JoinRateCommand] {
      case RegisterSubscriberActor(refActor) =>
        initContestJoinRateCache(context,replicator,Option(refActor))

      case AddToCache(key, value) =>
        replicator.askUpdate(
          askReplyTo => Update(RateLimitMapKey, LWWMap.empty[Int, Int], WriteLocal, askReplyTo)(_ :+ (key -> value)),
          InternalUpdateResponse.apply)
        Behaviors.same

      case RemoveCache(key) =>
        replicator.askUpdate(
          askReplyTo => Update(RateLimitMapKey, LWWMap.empty[Int, Int], WriteLocal, askReplyTo)(_ :+ (key -> 0)),
          InternalUpdateResponse.apply)
        Behaviors.same

      case GetCache(key, replyTo) =>
        replicator.askGet(
          askReplyTo => Get(RateLimitMapKey, ReadLocal, askReplyTo),
          rsp => InternalGetResponse(key, replyTo, rsp))

        Behaviors.same

      case InternalGetResponse(key, replyTo, g @ GetSuccess(_, _)) =>
        replyTo ! CachedValue(key, g.dataValue.get(key))
        Behaviors.same

      case InternalGetResponse(key, replyTo, _: NotFound[_]) =>
        replyTo ! CachedValue(key, None)
        Behaviors.same

      case InternalSubscribeResponse(c @ Changed(RateLimitMapKey)) =>
        c.get(RateLimitMapKey).entries.collect{
          case (key, value)  if value == 0 =>
            val element = RateLimitBackPressure(key,value)
            subscriberActorRef.map(ref => ref ! BackPressureRemoveEvent(element) )
          case (key, value)=>
            val element = RateLimitBackPressure(key,value)
            subscriberActorRef.map(ref => ref ! BackPressureUpdateEvent(element) )
        }
        Behaviors.same

      case InternalSubscribeResponse(_) =>
        println(s"recieved  event for unknown ")
        Behaviors.same
      case _: InternalGetResponse    => Behaviors.same // ok
      case _: InternalUpdateResponse => Behaviors.same // ok
    }

  }
}


/*
  final case class Update[A <: ReplicatedData](
      key: Key[A],
      writeConsistency: WriteConsistency,
      replyTo: ActorRef[UpdateResponse[A]])(val modify: Option[A] => A)
      extends Command
 */

/*
final case class Delete[A <: ReplicatedData](key: Key[A], consistency: WriteConsistency, request: Option[Any] = None)
      extends Command[A]
      with NoSerializationVerificationNeeded
 */
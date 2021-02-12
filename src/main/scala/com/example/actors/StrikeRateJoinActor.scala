package com.example.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.pattern.CircuitBreaker

import scala.concurrent.duration._
import akka.actor.typed.scaladsl.adapter._
import com.example.ratelimit.JoinRateLimiter
import com.example.ratelimit.JoinRateLimiter.RateLimitExceeded

import scala.concurrent.Future
object StrikeRateJoinActor {

  case class ContestJoinCircuitBreaker(contestId:Int,breaker:CircuitBreaker,joinRateLimiter:JoinRateLimiter)

  sealed trait StrikeRateJoinCommand
  final case class GetStrikeRate(contestId:Int, replyTo:ActorRef[Option[ContestJoinCircuitBreaker]]) extends StrikeRateJoinCommand
  final case class SubmitStrikeRate(contestId:Int,requests: Int, period: FiniteDuration) extends StrikeRateJoinCommand

  def apply(): Behavior[StrikeRateJoinCommand] = Behaviors.setup{context => strikeRate(context)}

  private def strikeRate(context: ActorContext[StrikeRateJoinCommand],contestJoinBreakers:Seq[ContestJoinCircuitBreaker] = Seq.empty): Behavior[StrikeRateJoinCommand] =
    Behaviors.receiveMessage {
      case GetStrikeRate(contestId,replyTo)  =>
        replyTo ! contestJoinBreakers.find(_.contestId == contestId)
        Behaviors.same
      case SubmitStrikeRate(contestId,requests,period)  =>
        val newBreakers = addToContestJoinBreaker(context,contestJoinBreakers,contestId,requests,period)
        strikeRate(context,newBreakers)
    }

  private def addToContestJoinBreaker(context: ActorContext[StrikeRateJoinCommand],
                                      contestJoinBreakers:Seq[ContestJoinCircuitBreaker],
                                      contestId:Int,
                                      requests: Int,
                                      period: FiniteDuration): Seq[ContestJoinCircuitBreaker] = {

     contestJoinBreakers.find(_.contestId == contestId) match {
      case  x:ContestJoinCircuitBreaker =>
        val updatedContestJoinBreaker = x.copy(joinRateLimiter = new JoinRateLimiter(requests,period))
        contestJoinBreakers.filter(_.contestId != x.contestId) :+ updatedContestJoinBreaker
      case None =>
        val breaker = CircuitBreaker(context.system.toClassic.scheduler, maxFailures = 5, callTimeout = 5.seconds, resetTimeout = 10.seconds)
        val limiter = new JoinRateLimiter(requests ,period)
        contestJoinBreakers :+ ContestJoinCircuitBreaker(contestId,breaker,limiter)
    }
  }

  //def contestJoinCall[T](breakerActorRef: ActorRef[Option[StrikeRateJoinCommand]],block: => Future[T]): Future[T] = {
    //breakerActorRef ?
  //}

}

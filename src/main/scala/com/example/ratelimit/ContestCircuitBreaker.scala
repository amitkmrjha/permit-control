package com.example.ratelimit

import akka.actor.typed.ActorSystem
import akka.pattern.{CircuitBreaker, CircuitBreakerOpenException}
import com.example.ratelimit.ContestCircuitBreaker.BreakerLimiter
import com.example.ratelimit.StrikeRateLimiter.RateLimitExceeded
import akka.actor.typed.scaladsl.adapter._

import scala.concurrent.duration._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.concurrent.Future
import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala

trait ContestCircuitBreaker  {
  def invoke[T](block: => Future[T])(implicit contestId:Int): Future[T]
}

class StrikeRateCircuitBreaker(system: ActorSystem[_], breaker:CircuitBreaker, limiter: RateLimiter) extends ContestCircuitBreaker {

  implicit val ec = system.executionContext

private val breakerCache: mutable.Map[Int, BreakerLimiter] = new ConcurrentHashMap[Int,BreakerLimiter]().asScala

  override def invoke[T](block: => Future[T])(implicit contestId:Int): Future[T] = {
    breakerCache.get(contestId) match {
      case  Some(x) => invoke(x.breaker,x.limiter,block)
      case None =>
        breakerCache + (contestId -> BreakerLimiter(breaker,limiter))
        invoke(breaker,limiter,block)
    }
  }

  private def invoke[T](breaker:CircuitBreaker, limiter: RateLimiter,block: => Future[T])(implicit contestId:Int): Future[T] = {
    breaker.withCircuitBreaker {
      limiter.call (block)
    }.recoverWith{
      case RateLimitExceeded => Future.failed(new Exception(s"Rate Limit invoked for contestId ${contestId}"))
      case _: CircuitBreakerOpenException => Future.failed(new Exception(s"CircuitBreakerOpenException for ${contestId}"))
    }
  }
}

object ContestCircuitBreaker {
  case class BreakerLimiter(breaker:CircuitBreaker, limiter: RateLimiter)
}



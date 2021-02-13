package com.example.ratelimit

import akka.actor.typed.ActorSystem
import akka.pattern.CircuitBreaker
import akka.actor.typed.scaladsl.adapter._

import scala.concurrent.Future
import scala.concurrent.duration._
trait PermitControl {
  protected val system: ActorSystem[_]
  protected lazy val breaker:CircuitBreaker = CircuitBreaker(system.toClassic.scheduler, maxFailures = 5, callTimeout = 5.seconds, resetTimeout = 10.seconds)
  protected lazy val rateLimiter:RateLimiter = new JoinRateLimiter(requests = 10, period = 10.seconds)
  protected lazy val circuitBreaker : ContestCircuitBreaker =  new StrikeRateCircuitBreaker(system,breaker,rateLimiter)
  protected def controlCall[T](block: => Future[T])(contestId:Int): Future[T] = {
    circuitBreaker.invoke(block)(contestId)
  }

}

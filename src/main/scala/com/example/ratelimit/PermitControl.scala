package com.example.ratelimit

import akka.actor.typed.ActorSystem
import akka.pattern.CircuitBreaker
import akka.actor.typed.scaladsl.adapter._

import scala.concurrent.Future
import scala.concurrent.duration._
trait PermitControl {
  protected val system: ActorSystem[_]
  protected lazy val circuitBreaker : ContestCircuitBreaker =  new StrikeRateCircuitBreaker(system)
  protected def controlCall[T](block: => Future[T])(contestId:Int): Future[T] = {
    circuitBreaker.invoke(block)(contestId)
  }

}

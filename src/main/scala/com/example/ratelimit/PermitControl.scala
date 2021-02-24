package com.example.ratelimit

import akka.actor.typed.ActorSystem
import akka.pattern.CircuitBreaker
import akka.actor.typed.scaladsl.adapter._
import com.example.ddata.ContestJoinRateCache

import scala.concurrent.Future
import scala.concurrent.duration._
trait PermitControl {
  protected val system: ActorSystem[_]
  protected lazy val requestFunnel : RequestFunnel =  new ContestFunnel()(system)
  protected def controlCall[T](block: => Future[T])(contestId:Int): Future[T] = {
    requestFunnel.invoke(block)(contestId)
  }
}

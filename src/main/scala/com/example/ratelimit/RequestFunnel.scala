package com.example.ratelimit

import akka.actor.typed.{ActorSystem, Scheduler}
import akka.pattern.{CircuitBreaker, CircuitBreakerOpenException}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.example.ddata.ContestJoinRateCache.{AddToCache, GetCache, RateLimitRef}
import com.example.domain.BackPressure
import com.example.ratelimit.RateLimiter.RateLimitExceeded
import com.example.ratelimit.RequestFunnel.ContestId
import com.typesafe.scalalogging.Logger

import scala.concurrent.duration._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala
import scala.util.Failure
import scala.util.Success

trait RequestFunnel  {
  def funnel[T](block: => Future[T])(implicit contestId:ContestId): Future[T]
  def updateFunnel(backPressure:BackPressure)
  def removeFunnel(contestId: ContestId)

}

class ContestFunnel()(implicit ec:ExecutionContext,timeout: Timeout,scheduler: Scheduler)extends RequestFunnel {

  val logger = Logger[ContestFunnel]

  private val funnelLocalMap: mutable.Map[Int, RateLimiter] = new ConcurrentHashMap[Int,RateLimiter]().asScala

  override def funnel[T](block: => Future[T])(implicit contestId:ContestId): Future[T] = {
    funnelLocalMap.get(contestId.id) match {
      case  Some(x) =>
        invoke(x,block)
      case None =>
        block
    }
  }

 override def updateFunnel(backPressure:BackPressure) = {
   val newRateLimiter = new JoinRateLimiter(backPressure.rate, period = backPressure.duration.getOrElse(10).seconds)
   funnelLocalMap.put(backPressure.contestId,newRateLimiter)
  }
  override def removeFunnel(contestId: ContestId) = {
    funnelLocalMap.remove(contestId.id)
  }

  private def invoke[T](limiter: RateLimiter,block: => Future[T])(implicit contestId:ContestId): Future[T] = {
    limiter.call (block).recover{
      case RateLimitExceeded => throw new Exception(s"Rate Limit invoked for contestId ${contestId}")
    }
  }
}

object RequestFunnel {
  case class ContestId(id:Int)
}



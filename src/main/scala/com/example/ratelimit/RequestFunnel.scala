package com.example.ratelimit

import akka.actor.typed.{ActorSystem, Scheduler}
import akka.pattern.{CircuitBreaker, CircuitBreakerOpenException}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.example.ddata.ContestJoinRateCache.{AddToCache, GetCache, RateLimitRef}
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
}

class ContestFunnel(rateLimitRef:RateLimitRef)(implicit ec:ExecutionContext,timeout: Timeout,scheduler: Scheduler)extends RequestFunnel {

  val logger = Logger[ContestFunnel]

  private val funnelLocalMap: mutable.Map[Int, RateLimiter] = new ConcurrentHashMap[Int,RateLimiter]().asScala

  override def funnel[T](block: => Future[T])(implicit contestId:ContestId): Future[T] = {
    funnelLocalMap.get(contestId.id) match {
      case  Some(x) =>
        logger.info(s"current rate limiter for Contest ${contestId} is ${x.currentRate}")
        if(x.isOverDue()){
          updateRateLimitCache(contestId.id,x.feedBackRate)
          getRateLimiter(contestId.id).flatMap { rateLimiter =>
            funnelLocalMap.put(contestId.id,rateLimiter)
            invoke(rateLimiter,block)
          }
        }
        invoke(x,block)
      case None =>
        getRateLimiter(contestId.id).flatMap { rateLimiter =>
            funnelLocalMap.put(contestId.id,rateLimiter)
            invoke(rateLimiter,block)
        }
    }
  }

  private def getRateLimiter(contestId:Int):Future[RateLimiter] = {
    rateLimitRef.ask(ref => GetCache(contestId,ref))
      .map { x =>
        logger.info(s"rate retrieved fom replicated cache ${x}")
        new JoinRateLimiter(x.value.getOrElse(10), period = 10.seconds)
      }
  }

  private def updateRateLimitCache(contestId:Int,rate:Int) = {
    rateLimitRef !  AddToCache(contestId, rate)
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



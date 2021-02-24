package com.example.ratelimit

import akka.actor.typed.{ActorSystem, Scheduler}
import akka.pattern.{CircuitBreaker, CircuitBreakerOpenException}
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.example.ddata.ContestJoinRateCache
import com.example.ddata.ContestJoinRateCache.{AddToCache, GetCache}
import com.example.ratelimit.RateLimiter.RateLimitExceeded
import com.typesafe.scalalogging.Logger

import scala.concurrent.duration._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable
import scala.concurrent.Future
import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala
import scala.util.Failure
import scala.util.Success

trait RequestFunnel  {
  def invoke[T](block: => Future[T])(implicit contestId:Int): Future[T]
}

class ContestFunnel()(implicit system: ActorSystem[_]) extends RequestFunnel {
  val logger = Logger[ContestFunnel]
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))
  implicit val executor = system.executionContext

  protected val  strikeRateCacheActor = system.systemActorOf(ContestJoinRateCache(),"replicatedCache")

  private val funnelLocalMap: mutable.Map[Int, RateLimiter] = new ConcurrentHashMap[Int,RateLimiter]().asScala

  override def invoke[T](block: => Future[T])(implicit contestId:Int): Future[T] = {
    funnelLocalMap.get(contestId) match {
      case  Some(x) =>
        logger.info(s"current rate limiter for Contest ${contestId} is ${x.currentRate}")
        if(x.isOverDue()){
          updateRateLimitCache(contestId,x.feedBackRate)
          getRateLimiter(contestId).flatMap { rateLimiter =>
            funnelLocalMap.put(contestId,rateLimiter)
            invoke(rateLimiter,block)
          }
        }
        invoke(x,block)
      case None =>
        getRateLimiter(contestId).flatMap { rateLimiter =>
            funnelLocalMap.put(contestId,rateLimiter)
            invoke(rateLimiter,block)
        }
    }
  }

  private def getRateLimiter(contestId:Int):Future[RateLimiter] = {
    strikeRateCacheActor.ask(ref => GetCache(contestId,ref))
      .map { x =>
        logger.info(s"rate retrieved fom replicated cache ${x}")
        new JoinRateLimiter(x.value.getOrElse(10), period = 10.seconds)
      }
  }

  private def updateRateLimitCache(contestId:Int,rate:Int) = {
    strikeRateCacheActor !  AddToCache(contestId, rate)
  }

  private def invoke[T](limiter: RateLimiter,block: => Future[T])(implicit contestId:Int): Future[T] = {
    limiter.call (block).recover{
      case RateLimitExceeded => throw new Exception(s"Rate Limit invoked for contestId ${contestId}")
    }
  }
}

object RequestFunnel {
}



package com.example.routes

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.example.JsonFormats
import com.example.ddata.ContestJoinRateCache.{AddToCache, CachedValue, GetCache, RateLimitRef, RemoveCache}
import com.example.domain.{BackPressure, RateLimitBackPressure, UpdateBackPressureRequest}
import com.example.ratelimit.JoinRateLimiter
import com.example.ratelimit.RequestFunnel.ContestId

import scala.concurrent.{ExecutionContext, Future}

class BackPressureRoutes(limiterRef: RateLimitRef) (implicit val system: ActorSystem[_]) {
  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  implicit val ec:ExecutionContext = system.executionContext
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  def getRate(contestId:ContestId): Future[BackPressure] = {
    limiterRef.ask(ref => GetCache(contestId.id,ref)).map {
      case v:CachedValue => RateLimitBackPressure(v.key,v.value.getOrElse(0))
    }
  }

  def updateRate(backPressure:BackPressure): Future[BackPressure] = {
    Future.successful(limiterRef  !  AddToCache(backPressure.contestId, backPressure.rate))
      .map(_ =>
        RateLimitBackPressure(backPressure.contestId, backPressure.rate)
      )
  }

  def removeRate(key:Int): Future[String] = {
    Future.successful(limiterRef  !  RemoveCache(key))
      .map(_ => s"Removed key ${key}"  )
  }

  val backPressureRoutes: Route =
    pathPrefix("contest.backpressure") {
      concat(
        path("contest"/IntNumber) { contestId =>
          concat(
            get {
              rejectEmptyResponse {
                onSuccess(getRate(ContestId(contestId))) { response =>
                  complete(response)
                }
              }
            },
            post {
              entity(as[UpdateBackPressureRequest]) { p =>
                complete(updateRate(RateLimitBackPressure(contestId,p.rate,p.duration)))
              }
            },
          delete {
              complete(removeRate(contestId))
          })
        })
    }
}

object BackPressureRoutes {
  def route(limiterRef: RateLimitRef)(implicit system: ActorSystem[_]) : Route= {
    new BackPressureRoutes(limiterRef)(system).backPressureRoutes
  }
}
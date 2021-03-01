package com.example.routes

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.example.JsonFormats
import com.example.ddata.ContestJoinRateCache.{AddToCache, CachedValue, GetCache, RateLimitRef}
import com.example.domain.BackPressure.BackPressureRate
import com.example.ratelimit.JoinRateLimiter
import com.example.ratelimit.RequestFunnel.ContestId

import scala.concurrent.{ExecutionContext, Future}

class BackPressureRoutes(limiterRef: RateLimitRef) (implicit val system: ActorSystem[_]) {
  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import spray.json.DefaultJsonProtocol._
  implicit val ec:ExecutionContext = system.executionContext
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  def getRate(contestId:ContestId): Future[String] = {
    limiterRef.ask(ref => GetCache(contestId.id,ref)).map {
      case v:CachedValue => v.value.getOrElse(0)
    }.map(p => p.toString)
  }

  def updateRate(contestId:ContestId,rate:Int): Future[String] = {
    Future.successful(limiterRef  !  AddToCache(contestId.id, rate)).map(_ => s"Done")
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
              entity(as[BackPressureRate]) { rate =>
                complete(updateRate(ContestId(contestId),rate.rate))
              }
            })
        })
    }
}

object BackPressureRoutes {
  def route(limiterRef: RateLimitRef)(implicit system: ActorSystem[_]) : Route= {
    new BackPressureRoutes(limiterRef)(system).backPressureRoutes
  }
}
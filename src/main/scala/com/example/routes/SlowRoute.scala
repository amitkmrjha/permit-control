package com.example.routes


import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, handleRejections, onSuccess, path}
import akka.http.scaladsl.server.{RejectionHandler, Route}
import akka.util.Timeout
import com.example.JsonFormats
import com.example.actors.SlowActor
import com.example.ratelimit.RateLimit.{Limiter, PathBusyRejection}
import akka.actor.typed.scaladsl.AskPattern._
import scala.concurrent.duration.DurationInt

class SlowRoute (slowActor: ActorRef[SlowActor.SlowCommand])(implicit val system: ActorSystem[_]) {
  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._

  private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  // Start of Rate Limit
  val rejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case PathBusyRejection(path, max) =>
        complete((StatusCodes.EnhanceYourCalm, s"Max concurrent requests for $path reached, please try again later"))
    }.result()

  // needs to be created outside of the route tree or else
  // you get separate instances rather than sharing one
  val limiter = new Limiter(max = 2)

  val route =
    handleRejections(rejectionHandler) {
      path("max-2") {
        limiter.limitConcurrentRequests {
          implicit val timeout: Timeout = 20.seconds
          onSuccess(slowActor.ask(SlowActor.Ping(_))) { _ =>
            complete("Done!")
          }
        }
      }
    }
}

object SlowRoute {
  def route(slowActor: ActorRef[SlowActor.SlowCommand])(implicit  system: ActorSystem[_]) : Route= {
    new SlowRoute(slowActor)(system).route
  }
}

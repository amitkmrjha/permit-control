package com.example.routes

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.coding.Coders
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.StatusCodes.MovedPermanently
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.pattern.ask
import akka.util.Timeout
import com.example.actors.RouteActor
import com.example.ddata.ContestJoinRateCache.RateLimitRef
import com.example.domain.ContestCommand

class TopLevelRoute(routeActors: ActorRef[RouteActor.ContestRequest],limiterRef: RateLimitRef)
                   (implicit system: ActorSystem[_]) {
  lazy val  route: Route =
    concat(
      ContextJoinRoutes.route(routeActors),
      BackPressureRoutes.route(limiterRef)
    )
}

package com.example.routes

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import com.example.JsonFormats
import com.example.actors.RouteActor
import com.example.actors.RouteActor.ContestJoin
import com.example.domain.ContestResponse

import scala.concurrent.Future

class ContextJoinRoutes(routeActor: ActorRef[RouteActor.ContestRequest])(implicit val system: ActorSystem[_]) {
  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))
  implicit val executor = system.executionContext

  def joinContest(user:String,contest:Int): Future[ContestResponse] = routeActor.ask(ContestJoin(user,contest,_))

  val contextJoinRoutes: Route =
    pathPrefix("contest-join") {
      concat(
        path(Segment/IntNumber) { (name , contest) =>
          concat(
            get {
              rejectEmptyResponse {
                onSuccess(joinContest(name,contest)) { response =>
                  complete(response)
                }
              }
            })
        })
    }
}

object ContextJoinRoutes {
  def route(routeActor: ActorRef[RouteActor.ContestRequest])(implicit system: ActorSystem[_]):Route = {
    new ContextJoinRoutes(routeActor)(system).contextJoinRoutes
  }
}
package com.example.routes

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import com.example.JsonFormats
import com.example.actors.RouteActors.{RActionPerformed, RCreateUser, RDeleteUser, RGetUser, RGetUserResponse, RGetUsers}
import com.example.actors.{RouteActors, User, Users}

import scala.concurrent.Future

class ContextJoinRoutes(routeRegistry: ActorRef[RouteActors.RouteCommand])(implicit val system: ActorSystem[_]) {
  import JsonFormats._
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("my-app.routes.ask-timeout"))

  def getUsers(): Future[Users] =
    routeRegistry.ask(RGetUsers)
  def getUser(name: String): Future[RGetUserResponse] =
    routeRegistry.ask(RGetUser(name, _))
  def createUser(user: User): Future[RActionPerformed] =
    routeRegistry.ask(RCreateUser(user, _))
  def deleteUser(name: String): Future[RActionPerformed] =
    routeRegistry.ask(RDeleteUser(name, _))

  val contextJoinRoutes: Route =
    pathPrefix("context.join") {
      concat(
        pathEnd {
          concat(
            get {
              complete(getUsers())
            },
            post {
              entity(as[User]) { user =>
                onSuccess(createUser(user)) { performed =>
                  complete((StatusCodes.Created, performed))
                }
              }
            })
        },
        path(Segment) { name =>
          concat(
            get {
              rejectEmptyResponse {
                onSuccess(getUser(name)) { response =>
                  complete(response.maybeUser)
                }
              }
            },
            delete {
              onSuccess(deleteUser(name)) { performed =>
                complete((StatusCodes.OK, performed))
              }
            })
        })
    }
}

object ContextJoinRoutes {
  def route(routeActor: ActorRef[RouteActors.RouteCommand])(implicit system: ActorSystem[_]):Route = {
    new ContextJoinRoutes(routeActor)(system).contextJoinRoutes
  }
}
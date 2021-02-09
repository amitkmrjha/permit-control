package com.example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, handleRejections, onSuccess, path}
import akka.http.scaladsl.server.{RejectionHandler, Route}
import akka.util.Timeout
import com.example.actors.{RouteActor, SlowActor, UserRegistry}
import com.example.ratelimit.RateLimit.{Limiter, PathBusyRejection}
import com.example.routes.{TopLevelRoute, UserRoutes}

import scala.concurrent.duration.DurationInt
import scala.io.StdIn
import scala.util.Failure
import scala.util.Success

//#main-class
object QuickstartApp {
  //#start-http-server
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    import system.executionContext

    val futureBinding = Http().newServerAt("localhost", 8080).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
  //#start-http-server
  def main(args: Array[String]): Unit = {
    //#server-bootstrapping
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val userRegistryActor = context.spawn(UserRegistry(), "UserRegistryActor")
      val routeActor = context.spawn(RouteActor(), "RouteActor")
      val slowActor = context.spawn(SlowActor(), "SlowActor")
      context.watch(userRegistryActor)

      val routes = new TopLevelRoute(userRegistryActor,routeActor,slowActor)(context.system).route
      startHttpServer(routes)(context.system)

      Behaviors.empty
    }
    val system = ActorSystem[Nothing](rootBehavior, "HelloAkkaHttpServer")
    StdIn.readLine() // let it run until user presses return
    system.terminate()
  }
}
//#main-class

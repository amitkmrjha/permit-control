package com.example.ratelimit
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.server.{Directive0, Rejection, RejectionHandler, Route}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import scala.util.{Failure, Success}
import java.util.concurrent.atomic.AtomicInteger

object RateLimit {

  case class PathBusyRejection(path: Uri.Path, max: Int) extends Rejection

  class Limiter(max: Int) {

    // needs to be a thread safe counter since there can be concurrent requests
    val concurrentRequests = new AtomicInteger(0)

    val limitConcurrentRequests: Directive0 =
      extractRequest.flatMap { request =>
        if (concurrentRequests.incrementAndGet() > max) {
          // we need to decrease it again, and then reject the request
          // this means you can use a rejection handler somwhere else, for
          // example around the entire Route turning all such rejections
          // to the same kind of actual HTTP response there
          concurrentRequests.decrementAndGet()
          reject(PathBusyRejection(request.uri.path, max))
        } else {
          mapResponse { response =>
            concurrentRequests.decrementAndGet()
            response
          }
        }

      }
  }

}

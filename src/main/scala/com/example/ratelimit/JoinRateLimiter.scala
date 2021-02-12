package com.example.ratelimit

import scala.concurrent.Future
import scala.concurrent.duration.{Deadline, FiniteDuration}

class JoinRateLimiter(requests: Int, period: FiniteDuration) {

  import com.example.ratelimit.JoinRateLimiter.RateLimitExceeded

  private val startTimes = {
    val onePeriodAgo = Deadline.now - period
    Array.fill(requests)(onePeriodAgo)
  }
  private var position = 0
  private def lastTime = startTimes(position)
  private def enqueue(time: Deadline) = {
    startTimes(position) = time
    position += 1
    if (position == requests) position = 0
  }
  def call[T](block: => Future[T]): Future[T] = {
    val now = Deadline.now
    if ((now - lastTime) < period) Future.failed(RateLimitExceeded)
    else {
      enqueue(now)
      block
    }
  }
}

object JoinRateLimiter {
  case object RateLimitExceeded extends RuntimeException
}
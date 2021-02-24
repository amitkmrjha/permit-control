package com.example.ratelimit

import com.example.ratelimit.RateLimiter.RateLimitExceeded
import com.typesafe.scalalogging.Logger

import scala.concurrent.Future
import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.util.Random

trait RateLimiter {
  def call[T](block: => Future[T]): Future[T]
  def isOverDue():Boolean
  def feedBackRate:Int
  def currentRate:Int
}
object RateLimiter {
  case object RateLimitExceeded extends RuntimeException
}

class JoinRateLimiter(requests: Int, period: FiniteDuration) extends RateLimiter {

  val logger = Logger[JoinRateLimiter]
  private var overDue = false

  private val startTimes: Array[Deadline] = {
    val onePeriodAgo = Deadline.now - period
    Array.fill(requests)(onePeriodAgo)
  }
  private var position = 0
  private def lastTime = startTimes(position)
  private def enqueue(time: Deadline) = {
    startTimes(position) = time
    position += 1
    if (position == requests) {
      overDue = true
      position = 0
    }
  }

  override def isOverDue() = overDue

  override def feedBackRate: Int = Random.between(1, 10)
  override def currentRate: Int = requests

  override def call[T](block: => Future[T]): Future[T] = {
    val now = Deadline.now
    if ((now - lastTime) < period) Future.failed(RateLimitExceeded)
    else {
      enqueue(now)
      block
    }
  }

}

object JoinRateLimiter {
}
/*
class JoinRateLimiter(requests: Int, period: FiniteDuration) extends RateLimiter {

  val logger = Logger[JoinRateLimiter]

  private val startTimes: Array[Deadline] = {
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
}*/

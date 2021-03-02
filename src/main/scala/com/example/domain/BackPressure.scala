package com.example.domain

import scala.concurrent.duration.FiniteDuration

trait BackPressure{
  def contestId:Int
  def rate : Int
  def duration : Option[Int]
}
case class RateLimitBackPressure(contestId:Int, rate:Int, duration:Option[Int]= None) extends BackPressure
case class UpdateBackPressureRequest(rate:Int,duration : Option[Int])


package com.example.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import scala.concurrent.duration._
object SlowActor {
  sealed trait SlowCommand
  case class Ping(replyTo:ActorRef[Pong]) extends SlowCommand
  case class Delay(replyTo:ActorRef[Pong]) extends SlowCommand
  case class Pong()

  def apply(): Behavior[SlowActor.SlowCommand ] = Behaviors.setup{context => registry(context)}

  private def registry(context: ActorContext[SlowCommand]): Behavior[SlowActor.SlowCommand] = {
    Behaviors.withTimers{ timers =>
      Behaviors.receiveMessage {
        case x: Ping =>
          println(s"starting timer on Ping receive")
          timers.startSingleTimer(Delay(x.replyTo),3.seconds)
          Behaviors.same
        case x: Delay =>
          println(s"replying Done()")
          x.replyTo ! Pong()
          Behaviors.same
      }
    }
  }
}

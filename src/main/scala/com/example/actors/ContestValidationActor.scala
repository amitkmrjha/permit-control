package com.example.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.example.domain.{ContestCommand, ContestJoinCommand, JoinContestResponse}

object ContestValidationActor {

  def apply(): Behavior[ContestCommand] = Behaviors.setup{context =>
    validate(context)
  }
  private def validate(context: ActorContext[ContestCommand]): Behavior[ContestCommand] =
    Behaviors.receiveMessage {
      case x:ContestJoinCommand =>
        println(s"ContestValidationActor received  ContestJoinCommand for ${x.userId},${x.contestId}")
        x.replyTo ! JoinContestResponse(s"Command to join contest is accepted for ${x.userId},${x.contestId}")
        Behaviors.same
    }

}

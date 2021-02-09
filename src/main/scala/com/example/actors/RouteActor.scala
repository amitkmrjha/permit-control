package com.example.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.example.domain.{ContestCommand, ContestJoinCommand, JoinContestResponse}

object RouteActor {

  sealed trait ContestRequest
  final case class ContestJoin(userId:String, contestId: Int,replyTo: ActorRef[JoinContestResponse]) extends ContestRequest

  def apply(): Behavior[ContestRequest] = Behaviors.setup{context =>
    val actorRef = context.spawn(ContestValidationActor(), "Validator-actor")
    registry(context,actorRef)
  }

  private def registry(context: ActorContext[ContestRequest],validatorActor: ActorRef[ContestCommand]): Behavior[ContestRequest] =
  Behaviors.receiveMessage {
    case x:ContestJoin =>
      val cmd = ContestJoinCommand(x.userId,x.contestId,x.replyTo)
      validatorActor.tell(cmd)
      Behaviors.same
  }
}

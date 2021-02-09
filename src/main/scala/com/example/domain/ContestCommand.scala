package com.example.domain
import akka.actor.typed.ActorRef
import com.example.actors.RouteActor.ContestRequest


sealed trait ContestCommand
final case class ContestJoinCommand(userId:String, contestId: Int,replyTo: ActorRef[JoinContestResponse]) extends ContestCommand
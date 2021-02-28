package com.example.domain
import akka.actor.typed.ActorRef
import com.example.actors.RouteActor.ContestRequest


sealed trait ContestCommand
final case class ContestJoinCommand(userId:String, contestId: Int,replyTo: ActorRef[ContestResponse]) extends ContestCommand
final case class WrappedResponse(result: ContestResponse, replyTo: ActorRef[ContestResponse]) extends ContestCommand
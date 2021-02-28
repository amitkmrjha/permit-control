package com.example.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.util.Timeout
import com.example.domain.{ContestCommand, ContestJoinCommand, JoinContestResponse, WrappedResponse}
import com.example.ddata.ContestJoinRateCache.RateLimitRef
import com.example.ratelimit.RequestFunnel.ContestId
import com.example.ratelimit.{ContestFunnel, RequestFunnel}

import scala.util.{Failure, Success, Try}
import scala.concurrent.Future

object ContestValidationActor {

  def apply(rateLimitRef:RateLimitRef): Behavior[ContestCommand] = Behaviors.setup{context =>
    val timeout = Timeout.create(context.system.settings.config.getDuration("my-app.routes.ask-timeout"))
    val requestFunnel : RequestFunnel =  new ContestFunnel(rateLimitRef)(context.executionContext,timeout,context.system.scheduler)
    validate(context,requestFunnel)
  }
  private def validate(context: ActorContext[ContestCommand],requestFunnel:RequestFunnel): Behavior[ContestCommand] =
    Behaviors.receiveMessage {
      case x:ContestJoinCommand =>
        implicit val contestId = ContestId(x.contestId)
        context.pipeToSelf(requestFunnel.funnel(mockReply(x))) {
            case Success(status:JoinContestResponse) => WrappedResponse( status,x.replyTo)
            case Failure(e) => WrappedResponse(JoinContestResponse(e.getMessage),x.replyTo)
        }
        Behaviors.same
      case res:WrappedResponse => res.replyTo ! res.result
        Behaviors.same
    }

  private def mockReply(cmd:ContestJoinCommand):Future[JoinContestResponse] = {
    println(s"ContestValidationActor received  ContestJoinCommand for ${cmd.userId},${cmd.contestId}")
    Future.successful(JoinContestResponse(s"Command to join contest is accepted for ${cmd.userId},${cmd.contestId}"))
  }

}

package com.example.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.util.Timeout
import com.example.domain.{BackPressureRemoveEvent, BackPressureUpdateEvent, ContestCommand, ContestJoinCommand, JoinContestResponse, WrappedResponse}
import com.example.ddata.ContestJoinRateCache.{RateLimitRef, RegisterSubscriberActor}
import com.example.ratelimit.RequestFunnel.ContestId
import com.example.ratelimit.{ContestFunnel, JoinRateLimiter, RequestFunnel}
import com.typesafe.scalalogging.Logger

import scala.util.{Failure, Success, Try}
import scala.concurrent.Future

object ContestValidationActor {

  val logger = Logger[ContestValidationActor.type]

  def apply(rateLimitRef:RateLimitRef): Behavior[ContestCommand] = Behaviors.setup{context =>

    val timeout = Timeout.create(context.system.settings.config.getDuration("my-app.routes.ask-timeout"))
    val requestFunnel : RequestFunnel =  new ContestFunnel()(context.executionContext,timeout,context.system.scheduler)

    rateLimitRef ! RegisterSubscriberActor(context.self)

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

      case event:BackPressureRemoveEvent =>
        requestFunnel.removeFunnel(ContestId(event.backPressure.contestId))
        Behaviors.same

      case event:BackPressureUpdateEvent =>
        requestFunnel.updateFunnel(event.backPressure)
        Behaviors.same
    }



  private def mockReply(cmd:ContestJoinCommand):Future[JoinContestResponse] = {
    logger.info(s"ContestValidationActor received  ContestJoinCommand for ${cmd.userId},${cmd.contestId}")
    Future.successful(JoinContestResponse(s"Command to join contest is accepted for ${cmd.userId},${cmd.contestId}"))
  }

}

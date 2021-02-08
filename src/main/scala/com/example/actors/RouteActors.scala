package com.example.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object RouteActors {
  // actor protocol
  sealed trait RouteCommand
  final case class RGetUsers(replyTo: ActorRef[Users]) extends RouteCommand
  final case class RCreateUser(user: User, replyTo: ActorRef[RActionPerformed]) extends RouteCommand
  final case class RGetUser(name: String, replyTo: ActorRef[RGetUserResponse]) extends RouteCommand
  final case class RDeleteUser(name: String, replyTo: ActorRef[RActionPerformed]) extends RouteCommand

  final case class RGetUserResponse(maybeUser: Option[User])
  final case class RActionPerformed(description: String)

  def apply(): Behavior[RouteCommand] = registry(Set.empty)

  private def registry(users: Set[User]): Behavior[RouteCommand] =
    Behaviors.receiveMessage {
      case RGetUsers(replyTo) =>
        replyTo ! Users(users.toSeq)
        Behaviors.same
      case RCreateUser(user, replyTo) =>
        replyTo ! RActionPerformed(s"User ${user.name} created.")
        registry(users + user)
      case RGetUser(name, replyTo) =>
        replyTo ! RGetUserResponse(users.find(_.name == name))
        Behaviors.same
      case RDeleteUser(name, replyTo) =>
        replyTo ! RActionPerformed(s"User $name deleted.")
        registry(users.filterNot(_.name == name))
    }
}

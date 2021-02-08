package com.example.actors

//#user-registry-actor
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

import scala.collection.immutable

//#user-case-classes
final case class User(name: String, age: Int, countryOfResidence: String)
final case class Users(users: immutable.Seq[User])
//#user-case-classe

object UserRegistry {
  // actor protocol
  sealed trait UserCommand
  final case class GetUsers(replyTo: ActorRef[Users]) extends UserCommand
  final case class CreateUser(user: User, replyTo: ActorRef[ActionPerformed]) extends UserCommand
  final case class GetUser(name: String, replyTo: ActorRef[GetUserResponse]) extends UserCommand
  final case class DeleteUser(name: String, replyTo: ActorRef[ActionPerformed]) extends UserCommand

  final case class GetUserResponse(maybeUser: Option[User])
  final case class ActionPerformed(description: String)

  def apply(): Behavior[UserCommand] = registry(Set.empty)

  private def registry(users: Set[User]): Behavior[UserCommand] =
    Behaviors.receiveMessage {
      case GetUsers(replyTo) =>
        replyTo ! Users(users.toSeq)
        Behaviors.same
      case CreateUser(user, replyTo) =>
        replyTo ! ActionPerformed(s"User ${user.name} created.")
        registry(users + user)
      case GetUser(name, replyTo) =>
        replyTo ! GetUserResponse(users.find(_.name == name))
        Behaviors.same
      case DeleteUser(name, replyTo) =>
        replyTo ! ActionPerformed(s"User $name deleted.")
        registry(users.filterNot(_.name == name))
    }
}
//#user-registry-actor

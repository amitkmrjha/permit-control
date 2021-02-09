package com.example.domain

sealed trait ContestResponse
final case class JoinContestResponse(description: String)
final case class ActionPerformed(description: String)


//final case class RGetUserResponse(maybeUser: Option[User])
//final case class RActionPerformed(description: String)
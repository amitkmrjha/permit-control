package com.example.domain

sealed trait ContestResponse

final case class JoinContestResponse(description: String) extends ContestResponse

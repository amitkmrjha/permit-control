package com.example

import com.example.actors.{User, Users}
import com.example.actors.UserRegistry.ActionPerformed
import com.example.domain.JoinContestResponse

//#json-formats
import spray.json.DefaultJsonProtocol

object JsonFormats  {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val userJsonFormat = jsonFormat3(User)
  implicit val usersJsonFormat = jsonFormat1(Users)

  implicit val actionPerformedJsonFormat = jsonFormat1(ActionPerformed)
  implicit val contestResponseJsonFormat = jsonFormat1(JoinContestResponse)
}
//#json-formats

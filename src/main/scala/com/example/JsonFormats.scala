package com.example

import com.example.actors.RouteActors.RActionPerformed
import com.example.actors.{User, Users}
import com.example.actors.UserRegistry.ActionPerformed

//#json-formats
import spray.json.DefaultJsonProtocol

object JsonFormats  {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._

  implicit val userJsonFormat = jsonFormat3(User)
  implicit val usersJsonFormat = jsonFormat1(Users)

  implicit val actionPerformedJsonFormat = jsonFormat1(ActionPerformed)
  implicit val ractionPerformedJsonFormat = jsonFormat1(RActionPerformed)
}
//#json-formats

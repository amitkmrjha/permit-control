package com.example
import com.example.domain.JoinContestResponse

//#json-formats
import spray.json.DefaultJsonProtocol

object JsonFormats  {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._
  implicit val contestResponseJsonFormat = jsonFormat1(JoinContestResponse)
}
//#json-formats

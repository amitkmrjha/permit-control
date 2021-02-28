package com.example
import com.example.domain.{ContestResponse, JoinContestResponse}
import spray.json._

//#json-formats
import spray.json.DefaultJsonProtocol

object JsonFormats  {
  // import the default encoders for primitive types (Int, String, Lists etc)
  import DefaultJsonProtocol._
  implicit val contestJoinResponseJsonFormat = jsonFormat1(JoinContestResponse)

  implicit val contestResponseFormat = new RootJsonFormat[ContestResponse] {
   def write(obj: ContestResponse): JsValue =
        JsObject((obj match {
          case c: JoinContestResponse => c.toJson
          case unknown => deserializationError(s"json deserialize error: $unknown")
        }).asJsObject.fields)

      def read(json: JsValue): ContestResponse =
        json.asJsObject.getFields("type") match {
          case Seq(JsString("JoinContestResponse")) => json.convertTo[JoinContestResponse]
          case unrecognized => serializationError(s"json serialization error $unrecognized")
        }
  }

}
//#json-formats

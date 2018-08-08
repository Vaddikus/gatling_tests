package inbox

import java.util.Base64
import spray.json._

case class All_Fields(unread: Int,
                      total: Int)

object inbox_JWTparser extends DefaultJsonProtocol {

  // defines a contract to deserialize the JSON object
  implicit val payloadJsonFormat: RootJsonFormat[All_Fields] = jsonFormat2(All_Fields)

  def getJSON(jwtToken: String): String = {
    val jwtTokenPayload = jwtToken.split('.')(1)

    new String(Base64.getDecoder.decode(jwtTokenPayload))
      .parseJson.toString()
  }

  def getUnread(jwtToken: String): Int = {
    val jwtTokenPayload = jwtToken.split('.')(1)

    new String(Base64.getDecoder.decode(jwtTokenPayload))
        .parseJson.convertTo[All_Fields].unread
  }

  def getTotal(jwtToken: String): Int = {
    val jwtTokenPayload = jwtToken.split('.')(1)

    new String(Base64.getDecoder.decode(jwtTokenPayload))
      .parseJson.convertTo[All_Fields].total
  }

}


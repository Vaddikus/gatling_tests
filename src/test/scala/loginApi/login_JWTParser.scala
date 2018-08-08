package loginApi

import java.util.Base64
import spray.json._

case class All_Fields(PlayerId: Int,
                      IsFirstLogin: Option[Boolean],
                      IsActive: Option[Boolean],
                      IsSelfExcluded: Option[Boolean],
                      SelfExclusionDate: Option[String],
                      SelfExclusionPeriod: Option[Int],
                      IsTimeouted: Option[Boolean],
                      TimeoutActivateDate: Option[String],
                      RegistrationDate: Option[String],
                      IsFrozen: Option[Boolean],
                      SiteId: Int,
                      SessionId: String,
                      SelfExclusionSource: Option[Int],
                      nbf: Int, exp: Int, iat: Int
                     )

object login_JWTparser extends DefaultJsonProtocol {

  // defines a contract to deserialize the JSON object
  implicit val payloadJsonFormat: RootJsonFormat[All_Fields] = jsonFormat16(All_Fields)

  def getJSON(jwtToken: String): String = {
    val jwtTokenPayload = jwtToken.split('.')(1)

    new String(Base64.getDecoder.decode(jwtTokenPayload))
      .parseJson.toString()
  }

  def getUsername(jwtToken: String): Int = {
    val jwtTokenPayload = jwtToken.split('.')(1)

    new String(Base64.getDecoder.decode(jwtTokenPayload))
        .parseJson.convertTo[All_Fields].PlayerId
  }

}


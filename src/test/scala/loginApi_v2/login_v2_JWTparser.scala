package loginApi_v2

import java.util.Base64

import spray.json._

case class All_Fields_v2(PlayerId: Int,
                         IsFirstLogin: Option[Boolean],
                         IsCustomerSelfExcluded: Option[Boolean],
                         IsBetPlacementEnabled: Option[Boolean],
                         RegistrationDate: Option[String],
                         SiteId: Int,
                         Version: String,
                         SessionId: String,
                         nbf: Int, exp: Int, iat: Int
                     )

object login_v2_JWTparser extends DefaultJsonProtocol {

  // defines a contract to deserialize the JSON object
  implicit val payloadJsonFormat: RootJsonFormat[All_Fields_v2] = jsonFormat11(All_Fields_v2)

  def getJSON(jwtToken: String): String = {
    val jwtTokenPayload = jwtToken.split('.')(1)

    new String(Base64.getDecoder.decode(jwtTokenPayload))
      .parseJson.toString
  }

}


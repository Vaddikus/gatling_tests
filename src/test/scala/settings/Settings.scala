package settings

import java.io.File
import com.typesafe.config.ConfigFactory

object Settings {
  //Data for Login API
  val activePlayerUsername = getProperty("activePlayer.username")
  val activePlayerPassword = getProperty("activePlayer.password")
  val activePlayerDOB = getProperty("activePlayer.DOB")

  val frozenPlayerUsername = getProperty("frozenPlayer.username")
  val frozenPlayerPassword = getProperty("frozenPlayer.password")

  val TOPlayerUsername = getProperty("TOPlayer.username")
  val TOPlayerPassword = getProperty("TOPlayer.password")

  val SEPlayerUsername = getProperty("SEPlayer.username")
  val SEPlayerPassword = getProperty("SEPlayer.password")
  val SEPlayerDOB = getProperty("SEPlayer.DOB")

  val LoginAPIsiteId = getProperty("LoginAPI.siteId")
  val LoginAPI10betUKsiteId = getProperty("LoginAPI.10betUK.siteId")

  val loginByEmail = getProperty("loginByEmail.email")
  val loginByEmailPassword = getProperty("loginByEmail.password")

  val options_auth = getProperty("options.authorization")

  //Data for Login API
  val SEPlayer_v2_Username = getProperty("SEPlayer_v2.username")
  val SEPlayer_v2_Password = getProperty("SEPlayer_v2.password")
  val SEPlayer_v2_SiteId = getProperty("SEPlayer_v2.siteId")


  //Data for Merchant API
  val site_id = getProperty("site.id")
  val player_id = getProperty("player.id")
  val token = getProperty("token")
  //Data for Player Data API
  val feederPlayerData = getProperty("feeder.playerData")
  val feederPlayerLimits = getProperty("feeder.playerLimits")
  val isHeadersInFile: Boolean = getProperty("is.headersInFile").toBoolean

  //Data for Inbox API
  val inboxPlayerID = getProperty("inbox.player.id")
  val inboxPlayerName = getProperty("inbox.player.name")
  val inboxPlayerPassword = getProperty("inbox.player.password")


  def getProperty(s: String):String = {
    val env = sys.props.getOrElse("env", "DEV")
    val config = ConfigFactory.parseFile(new File(s"src/test/resources/test.$env.properties"))
    config.getString(s)
  }

  def getURL(s: String):String = {
    getProperty(s + ".BaseURL")
  }
}

package playerApi

import java.io.File

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

class TestPlayerData extends Simulation {

  object PlayerInfo {

    val httpConf = http
      .baseURL(settings.Settings.getURL("playerData"))
      .acceptHeader("application/json")
      .contentTypeHeader("application/json")

    val feeder = csv(s"${settings.Settings.feederPlayerData}")
    val file = scala.io.Source.fromFile(new File("").getAbsolutePath + s"\\src\\test\\resources\\${settings.Settings.feederPlayerData}")
    val usersInFile = if (settings.Settings.isHeadersInFile) file.getLines().size - 1 else file.getLines().size

    val start = feed(feeder)
      .exec(http("Get player data (code:200)")
        .post("/PlayerDataMicroservice/GetPlayerData")
        .body(StringBody("{SiteId: ${SiteID}, PlayerReference: ${CustomerID}}"))
        .check(status is 200)
        .check(substring("\"AgentID\":"))
        .check(substring("\"CurrencyID\":"))
        .check(substring("\"CurrencyCode\":"))
        .check(substring("\"PlayerReference\":"))
        .check(substring("\"MerchantPlayerReference\":"))
        .check(substring("\"SiteID\":"))
        .check(substring("\"RegistrationCountry\":"))
        .check(substring("\"Limitations\":")))

    val startNegative = exec(http("Post incorrect request to get player data")
      .post("/PlayerDataMicroservice/GetPlayerData")
      .body(StringBody("{SiteId: 58, PlayerReference: 7123}"))
      .check(status is 200)
      .check(substring("\"PlayerData\":"))
      .check(substring("\"ResponseCode\":")))
  }

  val getPlayerData = scenario("Testing PlayerData API")
    .exec(PlayerInfo.start)

  val getNegativePlayerData = scenario("Testing Negative PlayerData API").exec(PlayerInfo.startNegative)

  setUp(
    getPlayerData.inject(rampUsers(PlayerInfo.usersInFile) over (1 seconds))
      .protocols(PlayerInfo.httpConf),
    getNegativePlayerData.inject(rampUsers(1) over (1 seconds)))
}

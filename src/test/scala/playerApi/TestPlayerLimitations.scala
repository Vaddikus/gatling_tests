package playerApi

import java.io.File

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._

class TestPlayerLimitations extends Simulation {

  object PlayerLimits {

    val httpConf = http
      .baseURL(settings.Settings.getURL("playerLimits"))
      .acceptHeader("application/json")
      .contentTypeHeader("application/json")

    val feeder = csv(s"${settings.Settings.feederPlayerLimits}")
    val file = scala.io.Source.fromFile(new File("").getAbsolutePath + s"\\src\\test\\resources\\${settings.Settings.feederPlayerLimits}")
    val usersInFile = if (settings.Settings.isHeadersInFile) file.getLines().size - 1 else file.getLines().size

    val getLimitations = feed(feeder)
      .exec(
        http("Get player limitations (code: 200)")
          .post(s"/PlayerLimitationsMicroservice/GetPlayerLimitations")
          .body(StringBody("{SiteId: ${SiteID}, PlayerReference: ${CustomerID}}"))
          .check(status is 200)
          .check(substring("\"LimitationType\"")))

  }

  val getLimits = scenario("Testing Player limitations MS").exec(PlayerLimits.getLimitations)

  setUp(
    getLimits.inject(rampUsers(PlayerLimits.usersInFile) over (5 seconds))
      .protocols(PlayerLimits.httpConf))
}

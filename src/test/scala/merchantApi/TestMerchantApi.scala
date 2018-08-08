package merchantApi

import io.gatling.core.Predef._
import io.gatling.http.Predef._

class TestMerchantApi extends Simulation {

  object MerchantFlags {
    val httpConf = http
      .baseURL(settings.Settings.getURL("merchantApi"))
      .acceptHeader("application/json")
      .contentTypeHeader("application/json")

    val checkStatus = http("Check status(code: 200)")
      .get("/status")
      .check(status is 200)
      .check(substring("\"health\":\"OK\""))

    val successTrue = exec(
      http("Set TRUE flag via Merchant API(code: 204)")
        .patch(s"/sites/${settings.Settings.site_id}/players/${settings.Settings.player_id}")
        .header("Authorization", s"${settings.Settings.token}")
        .body(StringBody("""[{"op":"replace","path":"/communication_prefs/promo_emails","value": true}]"""))
        .check(status is 204))

    val successFalse = exec(
      http("Set FALSE flag via Merchant API(code: 204)")
        .patch(s"/sites/${settings.Settings.site_id}/players/${settings.Settings.player_id}")
        .header("Authorization", s"${settings.Settings.token}")
        .body(StringBody("""[{"op":"replace","path":"/communication_prefs/promo_emails","value": false}]"""))
        .check(status is 204))

    val unauthorized = exec(
      http("Request without token(code: 401)")
        .patch(s"/sites/${settings.Settings.site_id}/players/${settings.Settings.player_id}")
        .body(StringBody("""[{"op":"replace","path":"/communication_prefs/promo_emails","value": true}]"""))
        .check(status is 401)
        .check(substring("Authentication fail")))

    val notFound = exec(
      http("Request with invalid player id(code: 404)")
        .patch(s"/sites/${settings.Settings.site_id}/players/700")
        .header("Authorization", s"${settings.Settings.token}")
        .body(StringBody("""[{"op":"replace","path":"/communication_prefs/promo_emails","value": true}]"""))
        .check(status is 404)
        .check(substring("Player not found")))

    val badWithInvalidOperation = exec(
      http("Request with invalid operation(code: 400)")
        .patch(s"/sites/${settings.Settings.site_id}/players/${settings.Settings.player_id}")
        .header("Authorization", s"${settings.Settings.token}")
        .body(StringBody("""[{"op":"","path":"/communication_prefs/promo_emails","value": true}]"""))
        .check(status is 400)
        .check(substring("Request parameters validation fail")))

    val badWithInvalidPath = exec(
      http("Request with invalid path(code: 400)")
        .patch(s"/sites/${settings.Settings.site_id}/players/7127")
        .header("Authorization", s"${settings.Settings.token}")
        .body(StringBody("""[{"op":"replace","path":"","value": false}]"""))
        .check(status is 400)
        .check(substring("Request parameters validation fail")))

    val badWithInvalidValue = exec(
      http("Request with invalid value(code: 400)")
        .patch(s"/sites/${settings.Settings.site_id}/players/7127")
        .header("Authorization", s"${settings.Settings.token}")
        .body(StringBody("""[{"op":"replace","path":"/communication_prefs/promo_emails","value": 1}]"""))
        .check(status is 400)
        .check(substring("Request parameters validation fail")))
  }

  val patchMerchant = scenario("Testing Merchant API")
    .exec(MerchantFlags.checkStatus)
    .exec(MerchantFlags.successTrue)
    .exec(MerchantFlags.successFalse)
    .exec(MerchantFlags.unauthorized)
    .exec(MerchantFlags.notFound)
    .exec(MerchantFlags.badWithInvalidOperation)
    .exec(MerchantFlags.badWithInvalidPath)
    .exec(MerchantFlags.badWithInvalidValue)

  setUp(
    patchMerchant.inject(atOnceUsers(1))
      .protocols(MerchantFlags.httpConf))
}

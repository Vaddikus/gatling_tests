package loginApi

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.response.{ResponseWrapper, StringResponseBody}


class TestLoginApi_v1_suffix extends Simulation {

  object Login {

    val httpConf = http
      .baseURL(settings.Settings.getURL("loginApi") + "/v1")
      .acceptHeader("application/json")
      .header("X-Forwarded-For", "92.68.47.154")
      .contentTypeHeader("application/json")

    var token = ""
    var UK_token = ""
    var logged_token = ""
    var usernameFirst = 0
    var usernameSecond = 0
    var pass = ""

    var getToken = exec(
      http("Get token by Site ID")
        .get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
        .header("Accept", "text/javascript")
        .check(status is 200)
        .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("token")))
      .exec(session => {
        token = session("token").as[String].trim
        //      println("Get token => " + token)
        session
      })

    val loginPositiveTest = exec(
      http("Login")
        .post("/api/login")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(s"""{"name":"${settings.Settings.activePlayerUsername}","password":"${settings.Settings.activePlayerPassword}","rememberMe": true}"""))
        .check(status is 200)
        .check(regex(""""storablePassword":".+[=]+"""").saveAs("pass"))
        .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").saveAs("logged_token")))
      .exec(session => {
        logged_token = session("logged_token").as[String].trim
        pass = session("pass").as[String].split("\":").get(1)
        session
      })


    // RememberMe tests
    //
    val rememberMeTrue =
    exec(http("RememberMe - Positive case")
      .post("/api/login")
      .header("Accept", "application/json")
      .header("Authorization", "Bearer " + "${token}")
      .body(StringBody(s"""{"name":"${settings.Settings.activePlayerUsername}","password":"${settings.Settings.activePlayerPassword}","rememberMe": true}"""))
      .check(status is 200)
      .check(regex(""""storablePassword":".+"""")))

    val rememberMeFalse =
      exec(http("RememberMe - Negative case")
        .post("/api/login")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(s"""{"name":"${settings.Settings.activePlayerUsername}","password":"${settings.Settings.activePlayerPassword}"}"""))
        .check(status is 200)
        .check(substring("storablePassword").notExists))

    // Login
    //

    val loginByToken =
      exec(_.set("password-token", pass))
          .exec(
      http("Login by token (token-login)")
        .post("/api/token-login")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody( s"""{ "username":"${settings.Settings.activePlayerUsername}","token":""" +  "${password-token} }"))
        .check(status is 200))


    val loginByEmail = exec(
      http("Login by email")
        .post("/api/login")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(s"""{"name":"${settings.Settings.loginByEmail}","password":"${settings.Settings.loginByEmailPassword}"}"""))
        .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("first_token")))
      .exec(session => {
        logged_token = session("first_token").as[String].trim
        session
      })
      .exec(
        //It is a fake GET-request. It's need to get non-empty body and substitute it
        http("Verify SiteID ").get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
          .transformResponse {
            case response if response.isReceived =>
              new ResponseWrapper(response) {
                val JSON = login_JWTparser.getJSON(logged_token)
                usernameFirst = login_JWTparser.getUsername(logged_token)
                override val body = new StringResponseBody(JSON, response.charset)
              }
          }.check(substring("SiteId\":56").exists))
      .exec(
        http("Get token for 10betUK ")
          .get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPI10betUKsiteId}")
          .header("Accept", "text/javascript")
          .check(status is 200)
          .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("UK_token")))
      .exec(session => {
        UK_token = session("token").as[String].trim
        //      println("Get token => " + token)
        session
      })
      .exec(
        http("Login with the same email and other token")
          .post("/api/login")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${UK_token}")
          .body(StringBody(s"""{"name":"${settings.Settings.loginByEmail}","password":"${settings.Settings.loginByEmailPassword}"}"""))
          .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("second_token")))
      .exec(session => {
        logged_token = session("second_token").as[String].trim
        session
      })
      .exec(
        //It is a fake GET-request. It's need to get non-empty body and substitute it
        http("Verify that SiteID and user were changed").get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
          .transformResponse {
            case response if response.isReceived =>
              new ResponseWrapper(response) {
                val JSON = login_JWTparser.getJSON(logged_token)
                usernameSecond = login_JWTparser.getUsername(logged_token)
                assert(usernameSecond != usernameFirst)
                override val body = new StringResponseBody(JSON, response.charset)
              }
          }
          .check(substring("SiteId\":1").exists)
      )

    val loginBySameName = exec(
      http("Get token by SiteID = 56")
        .post("/api/login")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(s"""{"name":"${settings.Settings.activePlayerUsername}","password":"${settings.Settings.activePlayerPassword}"}"""))
        .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("first_token")))
      .exec(session => {
        logged_token = session("first_token").as[String].trim
        session
      })
      .exec(
        //It is a fake GET-request. It's need to get non-empty body and substitute it
        http("Verify SiteID ").get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
          .transformResponse {
            case response if response.isReceived =>
                new ResponseWrapper(response) {
                val JSON = login_JWTparser.getJSON(logged_token)
                override val body = new StringResponseBody(JSON, response.charset)
              }
          }.check(substring("SiteId\":56").exists)
      )
      .exec(
        http("Get token by SiteID = 1 (10betUK)")
          .get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPI10betUKsiteId}")
          .header("Accept", "text/javascript")
          .check(status is 200)
          .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("UK_token")))
      .exec(session => {
        UK_token = session("token").as[String].trim
        //      println("Get token => " + token)
        session
      })
      .exec(
        http("Login with the same username and other token")
          .post("/api/login")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${UK_token}")
          .body(StringBody(s"""{"name":"${settings.Settings.activePlayerUsername}","password":"${settings.Settings.activePlayerPassword}"}"""))
          .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("second_token")))
      .exec(session => {
        logged_token = session("second_token").as[String].trim
        session
      })
      .exec(
        //It is a fake GET-request. It's need to get non-empty body and substitute it
        http("Verify that SiteID was changed").get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
          .transformResponse {
            case response if response.isReceived =>
               new ResponseWrapper(response) {
                val JSON = login_JWTparser.getJSON(logged_token)
                override val body = new StringResponseBody(JSON, response.charset)
              }
          }
          .check(substring("SiteId\":1").exists)
      )


    val loginWithInvalidToken = exec(
      http("Login with invalid token")
        .post("/api/token-login")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer 123" + "${token}")
        .body(StringBody(s"""{"name":"${settings.Settings.activePlayerUsername}","password":"${settings.Settings.activePlayerPassword}","rememberMe": true}"""))
        .check(status is 401))

    val loginWithInvalidStorablePassword = exec(
      http("Login with invalid storable password")
        .post("/api/token-login")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(s"""{ "username":"${settings.Settings.activePlayerUsername}","token": "${settings.Settings.activePlayerPassword}" }"""))
        .check(status is 400))

    val loginWithIncorrectPassword =
      exec(http("Login with incorrect password")
        .post("/api/login")
        .header("Authorization", "Bearer " + "${token}")
       // .body(StringBody(s"""{"name":"${settings.Settings.activePlayerUsername}","password":"654321","rememberMe": false}"""))
        .body(StringBody(s"""{"name":"crmtest1","password":"654321","rememberMe": false}"""))
        .check(status is 401)
        .check(substring(""""message":"INVALID_CREDENTIALS"""")))

    val loginWithEmptyPassword =
      exec(http("Login with empty password")
        .post("/api/login")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(s"""{"name":"${settings.Settings.activePlayerUsername}","password":"","rememberMe": false}"""))
        .check(status is 400)
        .check(substring(""""message":"LOGIN_OR_PASSWORD_EMPTY"""")))

    val loginWithIncorrectUsername =
      exec(http("Login with incorrect usernam")
        .post("/api/login")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(s"""{"name":"blabla","password":"${settings.Settings.activePlayerPassword}","rememberMe": false}"""))
        .check(status is 401)
        .check(substring(""""message":"INVALID_CREDENTIALS"""")))

    val loginWithEmptyUsername =
      exec(http("Login with empty username")
        .post("/api/login")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(s"""{"name":"","password":"${settings.Settings.activePlayerPassword}","rememberMe": false}"""))
        .check(status is 400)
        .check(substring(""""message":"LOGIN_OR_PASSWORD_EMPTY"""")))

    val logoutByToken = exec(
      http("Logout")
        .post("/api/logout")
        .header("Content-Type", "application/json")
        .header("Content-Length", "0")
        .header("Authorization", "Bearer ${logged_token}")
        .check(status is 200))

    val logoutWithInvalidToken =
      exec(http("Get invalid token")
        .get(s"/api/getTokenBySiteId/172")
        .header("Accept", "text/javascript")
        .check(status is 200)
        .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("bad_token")))
        .exec(session => {
          token = session("bad_token").as[String].trim
          session
        })
        .exec(
          http("Logout with invalid token")
            .post("/api/logout")
            .header("Content-Type", "application/json")
            .header("Content-Length", "0")
            .header("Authorization", "Bearer ${bad_token}")
            .check(status is 403))

    val getCaptchaImage = exec(http("Get token by site id")
      .get(s"/api/getTokenBySiteId/${settings.Settings.site_id}")
      .check(status is 200)
      .check(substring("ApiAccessToken"))
      .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").saveAs("token")))
      .exec(http("Get captcha image")
        .get(settings.Settings.getURL("captchaAPI") + "/api/captcha/image")
        .header("Authorization", "Bearer " + "${token}")
        .check(status is 200)
        .check(substring("captchaImage")))

    val loginByFrozenPlayer = exec(http("Get token by Site ID")
      .get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
      .header("Accept", "text/javascript")
      .check(status is 200)
      .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("token")))
      .exec(http("Login by Frozen Player")
        .post("/api/login")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(s"""{"name":"${settings.Settings.frozenPlayerUsername}","password":"${settings.Settings.frozenPlayerPassword}","rememberMe": true}"""))
        .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("Frozen_token")))
      .exec(session => {
        logged_token = session("Frozen_token").as[String].trim
        session
      }
      )
      .exec(
        //It is a fake GET-request. It's need to get non-empty body and substitute it
        http("Verify JSON with Frozen").get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
          .transformResponse {
            case response if response.isReceived =>
              new ResponseWrapper(response) {
                val JSON = login_JWTparser.getJSON(logged_token)
                override val body = new StringResponseBody(JSON, response.charset)
              }
          }.check(substring("IsFrozen\":true").exists)
      )

    val loginByTOPlayer = exec(http("Get token by Site ID")
      .get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
      .header("Accept", "text/javascript")
      .check(status is 200)
      .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("token")))
      .exec(http("Login by TO Player")
        .post("/api/login")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(s"""{"name":"${settings.Settings.TOPlayerUsername}","password":"${settings.Settings.TOPlayerPassword}","rememberMe": true}"""))
        .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("TO_token")))
      .exec(session => {
        logged_token = session("TO_token").as[String].trim
        session
      }
      )
      .exec(
        //It is a fake GET-request. It's need to get non-empty body and substitute it
        http("Verify JSON with TO").get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
          .transformResponse {
            case response if response.isReceived =>
              new ResponseWrapper(response) {
                val JSON = login_JWTparser.getJSON(logged_token)
                override val body = new StringResponseBody(JSON, response.charset)
              }
          }.check(substring("IsTimeouted\":true").exists)
      )

    val loginBySEPlayer = exec(http("Get token by Site ID")
      .get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
      .header("Accept", "text/javascript")
      .check(status is 200)
      .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("token")))
      .exec(http("Login by SE Player")
        .post("/api/login")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(s"""{"name":"${settings.Settings.SEPlayerUsername}","password":"${settings.Settings.SEPlayerPassword}","rememberMe": true}"""))
        .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("SE_token")))
      .exec(session => {
        logged_token = session("SE_token").as[String].trim
        session
      }
      )
      .exec(
        //It is a fake GET-request. It's need to get non-empty body and substitute it
        http("Verify JSON with SE").get(s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
          .transformResponse {
            case response if response.isReceived =>
              new ResponseWrapper(response) {
                val JSON = login_JWTparser.getJSON(logged_token)
                override val body = new StringResponseBody(JSON, response.charset)
              }
          }.check(substring("IsSelfExcluded\":true").exists)
      )

    val getOptions = exec(
      http("Get login options")
        .get("/api/login/options")
        .header("Content-Type", "application/json")
        .header("Authorization", s"${settings.Settings.options_auth}")
        .check(substring("anonymousJwtTokenExpiration"))
        .check(substring("jwtTokenExpiration"))
        .check(substring("failedAttemptsLimitByIp"))
        .check(substring("failedAttemptsLimitForOneTimeTokenLoginByIp"))
        .check(substring("failedAttemptsLimitByLogin"))
        .check(substring("ignoreLoginCache"))
        .check(status is 200))
  }

  val loginScenario = scenario("Testing Login API")
    .exec(Login.getToken)
    .exec(Login.loginPositiveTest)
  //  .exec(Login.loginByEmail)
  //  .exec(Login.loginBySameName)
    .exec(Login.rememberMeTrue)
    .exec(Login.rememberMeFalse)
    .exec(Login.loginByToken)
    .exec(Login.loginWithInvalidToken)
    .exec(Login.loginWithInvalidStorablePassword)
    .exec(Login.loginWithIncorrectPassword)
    .exec(Login.loginWithEmptyPassword)
    .exec(Login.loginWithIncorrectUsername)
    .exec(Login.loginWithEmptyUsername)
    .exec(Login.logoutByToken)
    .exec(Login.logoutWithInvalidToken)
    .exec(Login.getCaptchaImage)
    .exec(Login.loginByFrozenPlayer)
    .exec(Login.loginByTOPlayer)
    .exec(Login.loginBySEPlayer)
    .exec(Login.getOptions)


  setUp(
    loginScenario.inject(atOnceUsers(1))
      .protocols(Login.httpConf)
  )
}

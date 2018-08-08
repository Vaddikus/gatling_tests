package loginApi_v2

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.response.{ResponseWrapper, StringResponseBody}

/*
* BEFORE RUN TEST on DEV
* 1) Set for 10BetInternational restricted country = Antarctica (Configurations -> Operators -> tab Risk)
* 2) Turn on Support Date of birth (Configurations -> Operators -> tab Player Managment)
*
* FOR RUNNING TESTS ON STG/PROD
* - Login Api v2 is not on STG/PROD yet
* */

class TestLoginApi_v2 extends Simulation {

  object Login {

    val httpConf = http
      .baseURL(settings.Settings.getURL("loginApi_v2"))
      .acceptHeader("application/json")
      .header("X-Forwarded-For", "95.67.47.154")
      .contentTypeHeader("application/json")

    var token = ""
    var pass = ""
    var UK_token = ""
    var logged_token = ""

    var getToken = exec(
      http("Get token by Site ID")
        .get(s"/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
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
        .post("/login")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(
          s"""{"name":"${settings.Settings.activePlayerUsername}",
             |"password":"${settings.Settings.activePlayerPassword}",
             |"dateOfBirth":"${settings.Settings.activePlayerDOB}",
             |"rememberMe": true}""".stripMargin))
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
      .post("/login")
      .header("Accept", "application/json")
      .header("Authorization", "Bearer " + "${token}")
      .body(StringBody(
        s"""{"name":"${settings.Settings.activePlayerUsername}","password":"${settings.Settings.activePlayerPassword}",
           |"dateOfBirth":"${settings.Settings.activePlayerDOB}",
           |"rememberMe": true}""".stripMargin))
      .check(status is 200)
      .check(regex(""""storablePassword":".+"""")))

    val rememberMeFalse =
      exec(http("RememberMe - Negative case")
        .post("/login")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(
          s"""{"name":"${settings.Settings.activePlayerUsername}",
             |"dateOfBirth":"${settings.Settings.activePlayerDOB}",
             |"password":"${settings.Settings.activePlayerPassword}"}""".stripMargin))
        .check(status is 200)
        .check(substring("storablePassword").notExists))

    // Login
    //

    val loginByToken =
      exec(_.set("password-token", pass))
        .exec(
          http("Login by token (token-login)")
            .post("/token-login")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + "${token}")
            .body(StringBody( s"""{ "username":"${settings.Settings.activePlayerUsername}","token":""" + "${password-token} }"))
            .check(status is 200))


    val loginByEmail = exec(
      http("Login by email")
        .post("/login")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(
          s"""{"name":"${settings.Settings.loginByEmail}",
             |"dateOfBirth":"${settings.Settings.activePlayerDOB}",
             |"password":"${settings.Settings.loginByEmailPassword}"}""".stripMargin))
        .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("first_token")))
      .exec(session => {
        logged_token = session("first_token").as[String].trim
        session
      })
      .exec(
        //It is a fake GET-request. It's need to get non-empty body and substitute it
        http("Verify SiteID ").get(s"/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
          .transformResponse {
            case response if response.isReceived =>
              new ResponseWrapper(response) {
                val JSON = login_v2_JWTparser.getJSON(logged_token)
                override val body = new StringResponseBody(JSON, response.charset)
              }
          }.check(substring("SiteId\":56").exists))
      .exec(
        http("Get token for 10betUK ")
          .get(s"/getTokenBySiteId/${settings.Settings.LoginAPI10betUKsiteId}")
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
          .post("/login")
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
        http("Verify that SiteID was changed").get(s"/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
          .transformResponse {
            case response if response.isReceived =>
              new ResponseWrapper(response) {
                val JSON = login_v2_JWTparser.getJSON(logged_token)
                override val body = new StringResponseBody(JSON, response.charset)
              }
          }
          .check(substring("SiteId\":1").exists)
      )

    val loginBySameName = exec(
      http("Get token by SiteID = 56")
        .post("/login")
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
        http("Verify SiteID ").get(s"/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
          .transformResponse {
            case response if response.isReceived =>
              new ResponseWrapper(response) {
                val JSON = login_v2_JWTparser.getJSON(logged_token)
                override val body = new StringResponseBody(JSON, response.charset)
              }
          }.check(substring("SiteId\":56").exists)
      )
      .exec(
        http("Get token by SiteID = 1 (10betUK)")
          .get(s"/getTokenBySiteId/${settings.Settings.LoginAPI10betUKsiteId}")
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
          .post("/login")
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
        http("Verify that SiteID was changed").get(s"/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
          .transformResponse {
            case response if response.isReceived =>
              new ResponseWrapper(response) {
                val JSON = login_v2_JWTparser.getJSON(logged_token)
                override val body = new StringResponseBody(JSON, response.charset)
              }
          }
          .check(substring("SiteId\":1").exists)
      )

    val loginWithInvalidToken = exec(
      http("Login with invalid token")
        .post("/token-login")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer 123" + "${token}")
        .body(StringBody(s"""{"name":"${settings.Settings.activePlayerUsername}","password":"${settings.Settings.activePlayerPassword}","rememberMe": true}"""))
        .check(status is 401))

    val loginWithInvalidStorablePassword = exec(
      http("Login with invalid storable password")
        .post("/token-login")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody( s"""{ "username":"${settings.Settings.activePlayerUsername}","token": "${settings.Settings.activePlayerPassword}" }"""))
        .check(status is 400))

    val loginWithIncorrectPassword =
      exec(http("Login with incorrect password")
        .post("/login")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(
          s"""{"name":"${settings.Settings.activePlayerUsername}",
             |"dateOfBirth":"${settings.Settings.activePlayerDOB}",
             |"password":"654321","rememberMe": false}""".stripMargin))
        .check(status is 401)
        .check(substring(""""message":"INVALID_CREDENTIALS"""")))

    val loginWithEmptyPassword =
      exec(http("Login with empty password")
        .post("/login")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(
          s"""{"name":"${settings.Settings.activePlayerUsername}",
             |"dateOfBirth":"${settings.Settings.activePlayerDOB}",
             |"password":"","rememberMe": false}""".stripMargin))
        .check(status is 400)
        .check(substring(""""message":"LOGIN_OR_PASSWORD_EMPTY"""")))

    val loginWithIncorrectUsername =
      exec(http("Login with incorrect username")
        .post("/login")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(
          s"""{"name":"hello",
             |"password":"${settings.Settings.activePlayerPassword}",
             |"dateOfBirth":"${settings.Settings.activePlayerDOB}"}""".stripMargin))
        .check(status is 401)
        .check(substring(""""message":"INVALID_CREDENTIALS"""")))

    val loginWithEmptyUsername =
      exec(http("Login with empty username")
        .post("/login")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(
          s"""{"name":"",
             |"password":"${settings.Settings.activePlayerPassword}",
             |"dateOfBirth":"${settings.Settings.activePlayerDOB}",
             |"rememberMe": false}""".stripMargin))
        .check(status is 400)
        .check(substring(""""message":"LOGIN_OR_PASSWORD_EMPTY"""")))


    val loginWithoutDOB =
      exec(http("Login without Date of Birth")
        .post("/login")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(
          s"""{"name":"${settings.Settings.activePlayerUsername}",
             |"password":"${settings.Settings.activePlayerPassword}",
             |"rememberMe": false}""".stripMargin))
        .check(status is 400)
        .check(substring(""""message":"DATE_OF_BIRTH_EMPTY"""")))

    val loginWithUnknownCountry = exec(
      http("Login from unrecognised country")
        .post("/login")
        .header("Accept", "application/json")
        .header("X-Forwarded-For", "10.10.10.255")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(
          s"""{"name":"${settings.Settings.activePlayerUsername}",
             |"password":"${settings.Settings.activePlayerPassword}",
             |"dateOfBirth":"${settings.Settings.activePlayerDOB}"}""".stripMargin))
        .check(status is 401)
        .check(substring(""""message":"COUNTRY_IS_NOT_RECOGNIZED"""")))

    val loginWithForbiddenCountry = exec(
      http("Login from forbidden country")
        .post("/login")
        .header("Accept", "application/json")
        .header("X-Forwarded-For", "103.81.186.0")
        .header("Authorization", "Bearer " + "${token}")
        .body(StringBody(
          s"""{"name":"${settings.Settings.activePlayerUsername}",
             |"password":"${settings.Settings.activePlayerPassword}",
             |"dateOfBirth":"${settings.Settings.activePlayerDOB}"}""".stripMargin))
        .check(status is 401)
        .check(substring(""""message":"RESTRICTED_COUNTRY"""")))

    val logoutByToken = exec(
      http("Logout")
        .post("/logout")
        .header("Content-Type", "application/json")
        .header("Content-Length", "0")
        .header("Authorization", "Bearer ${logged_token}")
        .check(status is 200))

    val logoutWithInvalidToken =
      exec(http("Get invalid token")
        .get(s"/getTokenBySiteId/172")
        .header("Accept", "text/javascript")
        .check(status is 200)
        .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("bad_token")))
        .exec(session => {
          token = session("bad_token").as[String].trim
          session
        })
        .exec(
          http("Logout with invalid token")
            .post("/logout")
            .header("Content-Type", "application/json")
            .header("Content-Length", "0")
            .header("Authorization", "Bearer ${bad_token}")
            .check(status is 403))

    val getCaptchaImage = exec(http("Get token by site id")
      .get(s"/getTokenBySiteId/${settings.Settings.site_id}")
      .check(status is 200)
      .check(substring("token"))
      .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").saveAs("token")))
      .exec(http("Get captcha image")
        .get(settings.Settings.getURL("captchaAPI") + "/api/captcha/image")
        .header("Authorization", "Bearer " + "${token}")
        .check(status is 200)
        .check(substring("captchaImage")))


    val getSettings = exec(
      http("Get login settings")
        .get("/settings")
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
    .exec(Login.rememberMeTrue)
    .exec(Login.rememberMeFalse)
    .exec(Login.loginWithIncorrectUsername)
    .exec(Login.loginByToken)
    .exec(Login.loginWithInvalidToken)
    .exec(Login.loginWithInvalidStorablePassword)
    .exec(Login.loginWithIncorrectPassword)
    .exec(Login.loginWithEmptyPassword)
    .exec(Login.loginWithEmptyUsername)
    .exec(Login.loginWithoutDOB)
    .exec(Login.loginWithForbiddenCountry)
    .exec(Login.loginWithUnknownCountry)
    .exec(Login.logoutByToken)
    .exec(Login.logoutWithInvalidToken)
    .exec(Login.getCaptchaImage)
    .exec(Login.getSettings)


  setUp(
    loginScenario.inject(atOnceUsers(1))
      .protocols(Login.httpConf)
  )

}

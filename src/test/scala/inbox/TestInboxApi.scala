package inbox

import io.gatling.core.Predef._
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

class TestInboxApi extends Simulation {

  object Inbox {

    // tokens
    var token = ""
    var inbox_token = ""
    var logged_token = ""

    // amount of messages
    //    var unread = 0
    //    var total = 0

    // id, list of ids
    var list_ids: Vector[String] = null
    var outgoing_id = ""
    var message_id = ""
    var unread_message_id = ""
    var read_message_id = ""

    var temp_id = 0

    var unreadBeforePatching = 0
    var unreadAfterPatching = 0

    var unreadBeforeDeleting = 0
    var totalBeforeDeleting = 0


    // random value for each new message
    val randomValue: String = scala.util.Random.nextInt(100).toString

    // HTTP-CONF : base URL of Inbox Api
    val httpConf: HttpProtocolBuilder = http
      .baseURL(settings.Settings.getURL("inboxApi"))
      .acceptHeader("application/json")
      .contentTypeHeader("application/json")

    // LOGIN : Login to get token
    var login: ChainBuilder = exec(
      http("Get token by Site ID")
        .get(settings.Settings.getURL("loginApi") + s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
        .header("Accept", "text/javascript")
        .check(status is 200)
        .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("token")))
      .exec(session => {
        token = session("token").as[String].trim
        //      println("Get token => " + token)
        session
      }).
      exec(
        http("Login")
          .post(settings.Settings.getURL("loginApi") + "/api/login")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${token}")
          .body(StringBody(s"""{"name":"${settings.Settings.inboxPlayerName}","password":"${settings.Settings.inboxPlayerPassword}","rememberMe": true}"""))
          .check(status is 200)
          .check(regex(""""storablePassword":".+"""".split(":").get(1)).saveAs("pass"))
          .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").saveAs("logged_token")))
      .exec(session => {
        logged_token = session("logged_token").as[String].trim
        session
      })

    /**
      * COUNT MESSAGES : Count messages tests
      **/

    val countMessages: ChainBuilder = exec(
      http("Count messages")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/count")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .header("Content-Length", "0")
        .check(status is 200)
        .check(regex("unread\":\\d+").find.saveAs("unread"))
        .check(regex("total\":\\d+").find.saveAs("total")))
      .exec(session => {
        println("COUNT: Unread messages: " + session("unread").as[String].split(":").get(1))
        println("COUNT: Total messages: " + session("total").as[String].split(":").get(1))
        session
      })


    val countMessagesAfterPatching: ChainBuilder = exec(
      http("Count messages before patching")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/count")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .header("Content-Length", "0")
        .check(status is 200)
        .check(regex("\"unread\":\\d+").find.saveAs("unread_before")))
      .exec(
        http("Get all inbox messages and change status from unread to read")
          .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status is 200)
          .check(regex("\"isRead\":false,\"id\":\\d+").find.saveAs("unread_message_id")))
      .exec(session => {
        unread_message_id = session("unread_message_id").as[String].split("\"id\":").get(1)
        unreadBeforePatching = Integer.parseInt(session("unread_before").as[String].split(":").get(1))
        println("Unread messages before patching: " + session("unread_before").as[String])
        session
      })
      .exec(_.set("unreadMessageId", unread_message_id))
      .exec(http("Patch message as read")
        .patch(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/" + "${unreadMessageId}")
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody("{\"isRead\": true}"))
        .check(status is 200))
      .exec(_.set("afterPatch", unreadBeforePatching - 1))
      .exec(http("Count messages after patching")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/count")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .header("Content-Length", "0")
        .check(status is 200)
        .check(regex("\"unread\":\\d+").saveAs("unread_after"))
        .check(regex("unread\":\\d+".split(":").get(1))
          .find.transform(x => Integer.parseInt(x))
          .is("${afterPatch}")))
      .exec(
        session => {
          println("Unread messages after patching: " + session("unread_after").as[String])
          session
        })

    val countMessagesAfterRemovingRead: ChainBuilder = exec(http("Count messages before removing one READ")
      .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/count")
      .header("Accept", "application/json")
      .header("Authorization", "Bearer " + "${logged_token}")
      .header("Content-Length", "0")
      .check(status is 200)
      .check(regex("\"unread\":\\d+").find.saveAs("unread_before"))
      .check(regex("\"total\":\\d+").find.saveAs("total_before")))
      .exec(
        http("Get all inbox messages to find READ")
          .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status is 200)
          .check(regex("\"isRead\":true,\"id\":\\d+").find.saveAs("read_message_id")))
      .exec(session => {
        read_message_id = session("read_message_id").as[String].split("\"id\":").get(1)
        unreadBeforeDeleting = Integer.parseInt(session("unread_before").as[String].split(":").get(1))
        totalBeforeDeleting = Integer.parseInt(session("total_before").as[String].split(":").get(1))
        println("Total before removing READ: " + totalBeforeDeleting)
        println("Unread before removing READ: " + unreadBeforeDeleting)
        session
      })
      .exec(_.set("readMessageId", read_message_id))
      .exec(http("Delete read message")
        .delete(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/" + "${readMessageId}")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 204))
      .exec(_.set("totalAfterDelete", totalBeforeDeleting - 1))
      .exec(_.set("unreadAfterDelete", unreadBeforeDeleting))
      .exec(http("Count messages after deleting READ")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/count")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .header("Content-Length", "0")
        .check(status is 200)
        .check(jsonPath("$.unread").is("${unreadAfterDelete}").saveAs("unread_after"))
        .check(jsonPath("$.total").is("${totalAfterDelete}").saveAs("total_after")))
      .exec(
        session => {
          println("Total after removing READ: " + session("total_after").as[String])
          println("Unread after removing READ: " + session("unread_after").as[String])
          session
        })


    val countMessagesAfterRemovingUnread: ChainBuilder = exec(http("Count messages before removing one UNREAD")
      .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/count")
      .header("Accept", "application/json")
      .header("Authorization", "Bearer " + "${logged_token}")
      .header("Content-Length", "0")
      .check(status is 200)
      .check(regex("\"unread\":\\d+").find.saveAs("unread_before"))
      .check(regex("\"total\":\\d+").find.saveAs("total_before")))
      .exec(
        http("Get all inbox messages to find UNREAD")
          .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status is 200)
          .check(regex("\"isRead\":false,\"id\":\\d+").find.saveAs("unread_message_id")))
      .exec(session => {
        unread_message_id = session("unread_message_id").as[String].split("\"id\":").get(1)
        unreadBeforeDeleting = Integer.parseInt(session("unread_before").as[String].split(":").get(1))
        totalBeforeDeleting = Integer.parseInt(session("total_before").as[String].split(":").get(1))
        println("Total before removing UNREAD: " + totalBeforeDeleting)
        println("Unread before removing UNREAD: " + unreadBeforeDeleting)
        session
      })
      .exec(_.set("unreadMessageId", unread_message_id))
      .exec(http("Delete read message")
        .delete(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/" + "${unreadMessageId}")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 204))
      .exec(_.set("totalAfterDelete", totalBeforeDeleting - 1))
      .exec(_.set("unreadAfterDelete", unreadBeforeDeleting - 1))
      .exec(http("Count messages after deleting READ")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/count")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .header("Content-Length", "0")
        .check(status is 200)
        .check(jsonPath("$.unread").is("${unreadAfterDelete}").saveAs("unread_after"))
        .check(jsonPath("$.total").is("${totalAfterDelete}").saveAs("total_after")))
      .exec(
        session => {
          println("Total after removing UNREAD: " + session("total_after").as[String])
          println("Unread after removing UNREAD: " + session("unread_after").as[String])
          session
        })

    val countMessagesByAnotherPlayer: ChainBuilder = exec(http("Count messages by another player")
      .get(s"/players/7378/messages/inbox/count")
      .header("Accept", "application/json")
      .header("Authorization", "Bearer " + "${logged_token}")
      .header("Content-Length", "0")
      .check(status is 403))


    /**
      * CREATE MESSAGE : Create message tests
      */


    val createOutgoingMessage: ChainBuilder = exec(
      http("Create messages")
        .post(s"/players/${settings.Settings.inboxPlayerID}/messages?lang=en")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody(
          s"""{
          "subject": "string${randomValue}",
          "body": "string${randomValue}",
          "departmentId": 3,
          "originalMessageId": -1
        }"""))
        .check(status is 201)
        .check(substring("\"subject\":\"string" + randomValue))
        .check(substring("\"body\":\"string" + randomValue))
        .check(jsonPath("$.body.departmentId").is("3").saveAs("dep_id"))
        .check(regex("string" + randomValue).find.saveAs("body")))

    val createMessageWithoutSubject: ChainBuilder = exec(
      http("Create messages without subject")
        .post(s"/players/${settings.Settings.inboxPlayerID}/messages?lang=en")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody(
          s"""{
          "subject": "",
          "body": "string${randomValue}",
          "departmentId": 3,
          "originalMessageId": -1
        }"""))
        .check(status is 400)
        .check(substring("Validation Failed"))
        .check(substring("The Subject field is required.")))

    val createMessageWithoutBody: ChainBuilder = exec(
      http("Create messages without body")
        .post(s"/players/${settings.Settings.inboxPlayerID}/messages?lang=en")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody(
          s"""{
          "subject": "string${randomValue}",
          "body": "",
          "departmentId": 3,
          "originalMessageId": -1
        }"""))
        .check(status is 400)
        .check(substring("Validation Failed"))
        .check(substring("The Body field is required.")))

    val createMessageWithoutDepartment: ChainBuilder = exec(
      http("Create messages without department")
        .post(s"/players/${settings.Settings.inboxPlayerID}/messages?lang=en")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody(
          s"""{
          "subject": "string$randomValue",
          "body": "string$randomValue",
          "departmentId": "",
          "originalMessageId": -1
        }"""))
        .check(status is 400)
        .check(substring("Validation Failed"))
        .check(substring("The DepartmentId field is required.")))


    val createMessageByAnotherPlayer: ChainBuilder = exec(
      http("Create messages by another player")
        .post(s"/players/123/messages?lang=en")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody(
          s"""{
          "subject": "string$randomValue",
          "body": "string$randomValue",
          "departmentId": 3,
          "originalMessageId": -1
        }"""))
        .check(status is 403))

    val createMessageAsAnswer = exec(
      http("Get id from inbox message")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 200)
        .check(jsonPath("$.data[*].id").find.saveAs("message_id")))
      .exec(
        http("Create messages as answer")
          .post(s"/players/${settings.Settings.inboxPlayerID}/messages?lang=en")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .body(StringBody(
            s"""{
          "subject": "string$randomValue",
          "body": "string$randomValue",
          "departmentId": 3,
          "originalMessageId":""" + "${message_id}" + "}"))
          .check(status is 201)
          .check(substring("\"subject\":\"string" + randomValue))
          .check(substring("\"body\":\"string" + randomValue))
          .check(jsonPath("$.body.originalMessageId").is("${message_id}"))
          .check(jsonPath("$.body.departmentId").is("3"))
          .check(regex("string" + randomValue).find.saveAs("body")))
      .exec( session => {
        message_id = session("message_id").as[String]
        session
      })


    /**
      * DELETE MESSAGE : Delete message tests
      */

    val deleteInboxMessage: ChainBuilder = exec(_.set("messageToDelete", message_id))
      .exec(
        http("Delete inbox message")
          .delete(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/" + "${messageToDelete}")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status is 204))
      .exec(
        http("Check if deleted message is available")
          .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/" + "${messageToDelete}?lang=en")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status is 404))

    val deleteSentMessage: ChainBuilder = exec(
      http("Create outgoing message for deleting")
        .post(s"/players/${settings.Settings.inboxPlayerID}/messages?lang=en")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody(
          s"""{
          "subject": "string$randomValue",
          "body": "string$randomValue",
          "departmentId": 3,
          "originalMessageId": -1
        }"""))
        .check(status is 201)
        .check(substring("\"subject\":\"string" + randomValue))
        .check(substring("\"body\":\"string" + randomValue))
        .check(jsonPath("$.id").find.saveAs("id")))
      .exec(
        http("Attempt to delete sent message")
          .delete(s"/players/${settings.Settings.inboxPlayerID}/messages/sent/" + "${id}")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status is 404))

    val deleteAnotherInboxMessage: ChainBuilder =
      exec(
        http("Get id from inbox message to deleting")
          .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status is 200)
          .check(jsonPath("$.data[*].id").find.saveAs("id")))
        .exec(
          http("Get token for crmtest2 by Site ID")
            .get(settings.Settings.getURL("loginApi") + s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
            .header("Accept", "text/javascript")
            .check(status is 200)
            .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("token")))
        .exec(
          http("Login")
            .post(settings.Settings.getURL("loginApi") + "/api/login")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + "${token}")
            .body(StringBody("""{"name":"crmtest2","password":"123456"}"""))
            .check(status is 200)
            .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").saveAs("second_token")))
        .exec(
          http("Delete another's inbox message")
            .delete(s"/players/4892149/messages/inbox/" + "${id}")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + "${second_token}")
            .check(status is 403)
            .check(substring("Player 4892149 tries to delete message ${id} that does not belong to him.")))


    val deleteUnexistedInboxMessage: ChainBuilder = exec(
      http("Get id from inbox message to decrementing")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 200)
        .check(jsonPath("$.data[*].id").find.saveAs("id")))
      .exec(session => {
        temp_id = Integer.parseInt(session("id").as[String]) - 1000
        session
      })
      .exec(_.set("decId", temp_id))
      .exec(
        http("Delete unexisting inbox message")
          .delete(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/" + "${decId}")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status in(403, 404)))


    /**
      * GET DEPARTMENTS : Get departments
      **/

    val getAllDepartments: ChainBuilder = exec(
      http("Get departments")
        .get("/departments/en")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 200)
        .check(jsonPath("$.departmentsList[*].id").findAll.saveAs("departmentsList")))
      .exec(session => {
        println("Number of departments: " + session("departmentsList").as[List[String]].size)
        session
      })

    val checkCorrectDepartmentInSpecificMessage: ChainBuilder = exec(
      http("Get id from inbox message to check department")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 200)
        .check(jsonPath("$.data[*].id").find.saveAs("id")))
      .exec(http("Check department in specific message")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/" + "${id}?lang=en")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 200)
        .check(jsonPath("$.links[*].id").saveAs("department_id"))
        .check(jsonPath("$.data.department.lnk").is("${department_id}").saveAs("department_ids")))


    /**
      * GET MESSAGES : Get message
      **/

    val getInboxMessages: ChainBuilder = exec(
      http("Check that player gets all own messages")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 200)
        .check(jsonPath("$.data[*].playerReference").findAll.saveAs("list"))
        .check(regex("playerReference\":\"\\d+").findAll.saveAs("ids"))
        .check(jsonPath("$.data[*].id").find.saveAs("message_id")))
      .exec(session => {
        list_ids = session("list").as[Vector[String]]
        list_ids.foreach(x => assert(x.equals(settings.Settings.inboxPlayerID)))
        println("Amount of inbox messages: " + session("ids").as[Seq[String]].size)
        message_id = session("message_id").as[String]
        session
      })


    val getSpecificMessage: ChainBuilder = exec(
      http("Check specific message")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/${message_id}?lang=en")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 200)
        .check(jsonPath("$.data[*].id").is("${message_id}").saveAs("m_id"))
        .check(regex("\"playerReference\":\"" + settings.Settings.inboxPlayerID).find.exists))
      .exec(session => {
        println("Message id: " + session("m_id").as[String])
        session
      })

    val checkForAnotherMessages: ChainBuilder = exec(_.set("messageID", message_id))
      .exec(
        http("Check that player can't get another's messages")
          .get("/players/123/messages/inbox/${messageID}?lang=en")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status is 403))

    val checkPlayerGetOnlyInboxOutboxMessages: ChainBuilder = exec(
      http("Create messages for GetOnlyInboxOutbox")
        .post(s"/players/${settings.Settings.inboxPlayerID}/messages?lang=en")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody(
          s"""{
          "subject": "string$randomValue",
          "body": "string$randomValue",
          "departmentId": 3,
          "originalMessageId": -1
        }"""))
        .check(status is 201)
        .check(regex("string" + randomValue).find.saveAs("subject"))
        .check(jsonPath("$.id").find.saveAs("outgoing_id")))
      .exec(session => {
        outgoing_id = session("outgoing_id").as[String]
        session
      })
      .exec(_.set("outID", outgoing_id))
      .exec(
        http("Check that created message is among OUTBOX messages")
          .get(s"/players/${settings.Settings.inboxPlayerID}/messages/sent/" + "${outID}?lang=en")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status is 200))
      .exec(
        http("Check that created message is NOT among INBOX messages")
          .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status is 200)
          .check(regex("\"subject\":\"${subject}").find.notExists))


    val getNoMessages: ChainBuilder = exec(
      http("Get token by Site ID")
        .get(settings.Settings.getURL("loginApi") + s"/api/getTokenBySiteId/${settings.Settings.LoginAPIsiteId}")
        .header("Accept", "text/javascript")
        .check(status is 200)
        .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").find.saveAs("token_1")))
      .exec(
        http("Login for no messages")
          .post(settings.Settings.getURL("loginApi") + "/api/login")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${token_1}")
          .body(StringBody("""{"name":"securesolutions","password":"123456","rememberMe": true}"""))
          .check(status is 200)
          .check(regex("[a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+[.][a-zA-Z0-9_-]+").saveAs("nm_token")))
      .exec(
        http("Check that player gets nothing if he doesn't have messages")
          .get(s"/players/313457/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${nm_token}")
          .check(status is 200)
          .check(regex("playerReference\":\"\\d+").notExists))

    /**
      * PATCH MESSAGES : Patch messages as read
      **/

    val markMessageAsRead = exec(
      http("Get all inbox messages to changing status from unread to read")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 200)
        .check(regex("\"isRead\":false,\"id\":\\d+").find.saveAs("unread_message_id")))
      .exec(session => {
        unread_message_id = session("unread_message_id").as[String].split("\"id\":").get(1)
        session
      })
      .exec(_.set("unreadMessageId", unread_message_id))
      .exec(http("Mark(patch) message as read")
        .patch(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/" + "${unreadMessageId}")
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody("{\"isRead\": true}"))
        .check(status is 200))
      .exec(
        http("Check patched message")
          .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/" + "${unreadMessageId}?lang=en")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status is 200)
          .check(jsonPath("$.data.isRead").is("true"))
          .check(regex("\"playerReference\":\"" + settings.Settings.inboxPlayerID).find.exists))


    val markMessageAsUnread = exec(
      http("Get all inbox messages to changing status from read to unread")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 200)
        .check(regex("\"isRead\":true,\"id\":\\d+").find.saveAs("unread_message_id")))
      .exec(session => {
        read_message_id = session("unread_message_id").as[String].split("\"id\":").get(1)
        session
      })
      .exec(_.set("readMessageId", read_message_id))
      .exec(http("Attempt to mark(patch) message as unread")
        .patch(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/" + "${readMessageId}")
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody("{\"isRead\": false}"))
        .check(status is 422)
        .check(substring("Message can not be marked as unread")))


    val markSentMessage = exec(
      http("Create outgoing messages to mark it")
        .post(s"/players/${settings.Settings.inboxPlayerID}/messages?lang=en")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody(
          s"""{
          "subject": "string${randomValue}",
          "body": "string${randomValue}",
          "departmentId": 3,
          "originalMessageId": -1
        }"""))
        .check(status is 201)
        .check(substring("\"subject\":\"string" + randomValue))
        .check(substring("\"body\":\"string" + randomValue))
        .check(jsonPath("$.id").saveAs("sent_id")))
      .exec(http("Attempt to mark(patch) SENT message as read")
        .patch(s"/players/${settings.Settings.inboxPlayerID}/messages/sent/" + "${sent_id}")
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody("{\"isRead\": true}"))
        .check(status is 404))
    /*
  * TODO
  * */

    val markAsReadInboxAnotherMessage = exec(
      http("Get all inbox messages to mark as read with other player")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 200)
        .check(jsonPath("$.data[*].id").find.saveAs("message_id")))
      .exec(http("Mark(patch) another message as read")
        .patch("/players/123/messages/inbox/${message_id}")
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .body(StringBody("{\"isRead\": true}"))
        .check(status is 200))
      .exec(
        http("Check patched message")
          .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/" + "${unreadMessageId}?lang=en")
          .header("Accept", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .check(status is 200)
          .check(jsonPath("$.data.isRead").is("true"))
          .check(regex("\"playerReference\":\"" + settings.Settings.inboxPlayerID).find.exists))


    val markAsReadUnexistedInboxMessage: ChainBuilder = exec(
      http("Get id from inbox message to decrementing")
        .get(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox?lang=en&loadBody=true&page=0&per_page=100")
        .header("Accept", "application/json")
        .header("Authorization", "Bearer " + "${logged_token}")
        .check(status is 200)
        .check(jsonPath("$.data[*].id").find.saveAs("id")))
      .exec(session => {
        temp_id = Integer.parseInt(session("id").as[String]) - 100
        session
      })
      .exec(_.set("tempId", temp_id))
      .exec(
        http("Patch unexisting inbox message")
          .patch(s"/players/${settings.Settings.inboxPlayerID}/messages/inbox/" + "${tempId}")
          .header("Accept", "application/json")
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer " + "${logged_token}")
          .body(StringBody("{\"isRead\": true}"))
          .check(status in (403, 404)))

  }

  val inbox = scenario("Testing Inbox api")
    .exec(Inbox.login)
    .exec(Inbox.countMessages)
    .exec(Inbox.countMessagesAfterPatching)
    .exec(Inbox.countMessagesAfterRemovingRead)
    .exec(Inbox.countMessagesAfterRemovingUnread)
    .exec(Inbox.countMessagesByAnotherPlayer)
    .exec(Inbox.createOutgoingMessage)
    .exec(Inbox.createMessageWithoutSubject)
    .exec(Inbox.createMessageWithoutBody)
    .exec(Inbox.createMessageWithoutDepartment)
    .exec(Inbox.createMessageByAnotherPlayer)
    .exec(Inbox.createMessageAsAnswer)
    .exec(Inbox.deleteInboxMessage)
    .exec(Inbox.deleteAnotherInboxMessage)
    .exec(Inbox.deleteUnexistedInboxMessage)
    .exec(Inbox.getAllDepartments)
    .exec(Inbox.checkCorrectDepartmentInSpecificMessage)
    .exec(Inbox.getInboxMessages)
    .exec(Inbox.getSpecificMessage)
    .exec(Inbox.checkForAnotherMessages)
    .exec(Inbox.checkPlayerGetOnlyInboxOutboxMessages)
    .exec(Inbox.getNoMessages)
    .exec(Inbox.markMessageAsRead)
    .exec(Inbox.markMessageAsUnread)
    .exec(Inbox.markSentMessage)
    .exec(Inbox.markAsReadUnexistedInboxMessage)


  setUp(inbox.inject(atOnceUsers(1)).protocols(Inbox.httpConf)
  )
}

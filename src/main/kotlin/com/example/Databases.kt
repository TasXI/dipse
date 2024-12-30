package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mongodb.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Dictionary

fun Application.configureDatabases() {
    val mongoDatabase = connectToMongoDB()
    val userdService = UserdService(mongoDatabase)
    val messageResponseFlow = MutableSharedFlow<List<String>>()
    val sharedFlow = messageResponseFlow.asSharedFlow()

    routing {

        post("/signin") {
            var lp: LoginRequest = call.receive<LoginRequest>()

            var us = userdService.getUser(lp)

            if (us != null) {
                val token = JWT.create()
                    .withAudience(Consts.audience)
                    .withSubject(us.user.username)
                    .sign(Algorithm.HMAC256(Consts.secret))

                us.token = token

                call.respond(HttpStatusCode.Accepted, us)
            } else call.respond(HttpStatusCode.NotFound)
        }

        webSocket("/eventssub") {


            var clientId: String = ""

            val job2 = launch {
                sharedFlow.collect { ids ->
                    if (ids.drop(1).contains(clientId)) {
                        send(Frame.Text(ids[0]))
                    }
                }
            }

            val job = launch {
                while (true) {
                    send(Frame.Ping(byteArrayOf()))
                    delay(5000)
                }
            }

                sendSerialized("connect successful")


            try {
                for (frame in incoming){
                    val text = (frame as Frame.Text).readText()
                    println("onMessage")
                    clientId = text
                }
            } catch (e: ClosedReceiveChannelException) {
                println("onClose ${closeReason.await()}")
            } catch (e: Throwable) {
                println("onError ${closeReason.await()}")
                e.printStackTrace()
            }
            finally {
                job.cancel()
                job2.cancel()
            }
        }



        authenticate {

            post("/chatmessagesaddone"){
                try {
                    val mes_chat = call.receive<Pair<Message, ChatRecGet>>()
                    val isOk = userdService.putMessage(mes_chat.first, mes_chat.second)
                    if (isOk) {

                        val list = mutableListOf<String>()
                        list.add("messages")
                        list.addAll(mes_chat.second.userIds)
                        messageResponseFlow.emit(list)
                        call.respond(HttpStatusCode.OK)
                    }
                    else call.respond(HttpStatusCode.NotModified)
                }catch (_:Exception){
                    call.respond(HttpStatusCode.NotModified)
                }
            }

            post("/chatmessagesgets"){
                val mid = call.receive<ChatRecGet>()

                val messages = userdService.getMessageIds(mid.chatId)

                if (messages == null){
                    call.respond(HttpStatusCode.NotFound)
                }else{
                    call.respond(HttpStatusCode.OK, messages)
                }
            }

            post("/chatgets") {

                val userId = call.receive<String>()

                val chatsIds = userdService.getChatsIds(userId)

                if (chatsIds != null) {
                    val chats = userdService.getChats(chatsIds)

                    if (chats != null) {
                        call.respond(HttpStatusCode.OK, chats)
                    } else call.respond(HttpStatusCode.NotFound)
                } else call.respond(HttpStatusCode.NotFound)
            }

            post("/chatcreate") {

                val chatRec = call.receive<ChatRecPut>()

                val chatId = userdService.createChat(chatRec)

                if (chatId == null) {
                    call.respond(HttpStatusCode.NotModified)
                } else {
                    val res = userdService.addChatToUsers(chatId, chatRec.userIds)

                    if (res == null || res == false) {
                        call.respond(HttpStatusCode.NotModified)
                    } else {

                        var action = mutableListOf<String>()
                        action.add("chats")
                        action.addAll(chatRec.userIds)
                        messageResponseFlow.emit(action)

                        call.respond(HttpStatusCode.OK, res)
                    }
                }
            }


            post("/userrole") {

                val roleData = call.receive<String>()

                val listUsers = userdService.getUsersByRole(roleData)

                if (listUsers != null) {
                    call.respond(HttpStatusCode.OK, listUsers)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            post("/signup") {

                val userData = call.receive<User>()
                val res = userdService.addUser(userData)
                if (res == true) {
                    call.respond(HttpStatusCode.Created)
                } else {
                    call.respond(HttpStatusCode.NotModified)
                }
            }


        }
        // Read car
//        get("/users/{id}") {
//            val id = call.parameters["id"] ?: throw IllegalArgumentException("No ID found")
//            userdService.read(id)?.let { user ->
//                call.respond(user)
//            } ?: call.respond(HttpStatusCode.NotFound)
//        }
        // Update car
        put("/cars/{id}") {

        }
        // Delete car
        delete("/cars/{id}") {

        }
    }
}

fun Application.connectToMongoDB(): MongoDatabase {

    val uri = "mongodb+srv://otamakhin:OMJLmUI2ls7IhiR7@tamakhinoo.dk3c4.mongodb.net/?retryWrites=true&w=majority&appName=tamakhinOO"

    val mongoClient = MongoClients.create(uri)
    val database = mongoClient.getDatabase("khai")

    monitor.subscribe(ApplicationStopped) {
        mongoClient.close()
    }

    return database
}


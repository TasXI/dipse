package com.example

import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.bson.Document
import org.bson.types.ObjectId


@Serializable
data class LoginRequest(val username: String, val password: String)


@Serializable
open class UserC(
    var username: String = "",
    var name1: String = "",
    var name2: String = "",
    var name3: String = "",
    var role: String = "",
)
{

}

@Serializable
data class UserRec(
    var username: String = "",
    var password: String = "",
    var name1: String = "",
    var name2: String = "",
    var name3: String = "",
    var role: String = ""
) {
    fun toDocument(): Document = Document.parse(Json.encodeToString(this))

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromDocument(document: Document): UserRec = UserRec.json.decodeFromString(document.toJson())
    }
}

@Serializable
data class UserInfo(var id: String = "", val username: String){
    fun toDocument(): Document = Document.parse(Json.encodeToString(this))

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromDocument(document: Document): UserInfo = UserInfo.json.decodeFromString(document.toJson())
    }
}


@Serializable
open class UserClient(
    var user: UserC,
    var id: String,
    var token: String = ""
)

@Serializable
open class Message(
    var userIdSend: String,
    var username: String,
    var messageText: String
){
    fun toDocument(): Document = Document.parse(Json.encodeToString(this))

    companion object {
        private val json = Json { ignoreUnknownKeys = true
        }

        fun fromDocument(document: Document): Message = json.decodeFromString(document.toJson())
    }
}

@Serializable
open class ChatRecPut(
    var chatName: String,
    var userIds: List<String>,
    var messages: List<Message>
)
{
    fun toDocument(): Document = Document.parse(Json.encodeToString(this))

    companion object {
        private val json = Json { ignoreUnknownKeys = true
        }

        fun fromDocument(document: Document): ChatRecPut = json.decodeFromString(document.toJson())
    }
}

@Serializable
open class ChatRecGet(
    var chatId: String = "",
    var chatName: String,
    var userIds: List<String>,
    var lastMessage: Message? = null
){
    fun toDocument(): Document = Document.parse(Json.encodeToString(this))

    companion object {
        private val json = Json { ignoreUnknownKeys = true
            encodeDefaults = true}

        fun fromDocument(document: Document): ChatRecGet = json.decodeFromString(document.toJson())
    }
}

@Serializable
data class User(
    var username: String = "",
    var password: String = "",
    var name1: String = "",
    var name2: String = "",
    var name3: String = "",
    var id: String = "",
    var role: String = ""
) {
    fun toDocument(): Document = Document.parse(Json.encodeToString(this))

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromDocument(document: Document): User = json.decodeFromString(document.toJson())
    }
}


class UserdService(private val database: MongoDatabase) {
    var collectionUsers: MongoCollection<Document>
    var collectionChats: MongoCollection<Document>



    init {
        database.createCollection("users")
        collectionUsers = database.getCollection("users")
        database.createCollection("chats")
        collectionChats = database.getCollection("chats")
    }


    suspend fun getChats(ids: List<String>): List<ChatRecGet>? = withContext(Dispatchers.IO) {

        var idsOb = ids.map { ObjectId(it) }

        collectionChats.find(Filters.`in`("_id", idsOb))?.let {

            val list = mutableListOf<ChatRecGet>()

            it.forEach { doc ->

                val rec: ChatRecGet = ChatRecGet.fromDocument(doc)
                rec.chatId = doc.getObjectId("_id").toString()
                val listge = doc.getList("messages", Document::class.java)
                if (listge.size > 0) rec.lastMessage = Message.fromDocument(listge.last())
                list.add(rec)
            }

            list

        }
    }



    suspend fun putMessage(message: Message, chatRecGet: ChatRecGet): Boolean = withContext(Dispatchers.IO) {
        collectionChats.updateMany(Filters.eq("_id", ObjectId(chatRecGet.chatId)), Updates.push("messages", message.toDocument())).wasAcknowledged()
    }

    suspend fun getMessageIds(id: String): List<Message>? = withContext(Dispatchers.IO) {
        collectionChats.find(Filters.eq("_id", ObjectId(id))).first()?.getList("messages", Document::class.java)
            ?.map { Message.fromDocument(it) }
    }

    suspend fun getChatsIds(id: String): List<String>? = withContext(Dispatchers.IO) {

        collectionUsers.find(Filters.eq("_id", ObjectId(id))).first()?.let {
            it.getList("chats", ObjectId::class.java).map { it.toString() }
        }

    }

    suspend fun createChat(chat :ChatRecPut) : String? = withContext(Dispatchers.IO){

        collectionChats.insertOne(chat.toDocument())?.let {
            it.insertedId?.asObjectId()?.value.toString()
        }
    }

    suspend fun addChatToUsers(chatId: String, usersId: List<String>): Boolean? = withContext(Dispatchers.IO){

        var ids = usersId.map { ObjectId(it) }

        collectionUsers.updateMany(Filters.`in`("_id", ids), Updates.push("chats", ObjectId(chatId)))?.let {
            it.wasAcknowledged()
        }

    }

    suspend fun getUser(user: LoginRequest): UserClient? = withContext(Dispatchers.IO) {
        collectionUsers.find(Filters.and(Filters.eq("password", user.password),
            Filters.eq("username", user.username))).first()?.let {
            val indoc = UserRec.fromDocument(it)
            UserClient(
                UserC(indoc.username, indoc.name1, indoc.name2, indoc.name3, indoc.role),
                it.getObjectId("_id").toString()
            )
        }

    }


    suspend fun addUser(user: User): Boolean? = withContext(Dispatchers.IO) {
        try {
            val addUser = UserRec(user.username, user.password, user.name1, user.name2, user.name3, "student")
            val doc = addUser.toDocument()
            collectionUsers.insertOne(doc).wasAcknowledged()
        }
        catch(e: Exception){
            false
        }
    }

    suspend fun getUsersByRole(role: String) : List<UserInfo>? = withContext(Dispatchers.IO){

        collectionUsers.find(Filters.eq("role", role))?.let{

            val docList =   it.toList()
            val userList: MutableList<UserInfo> = mutableListOf()
            docList.forEach<Document>
            { doc ->

                val user = UserInfo(doc.getObjectId("_id").toString(),doc.getString("username"))

                userList.add(user)
            }

            userList
        }
    }



    suspend fun update(id: String, car: User): Document? = withContext(Dispatchers.IO) {
        collectionUsers.findOneAndReplace(Filters.eq("_id", ObjectId(id)), car.toDocument())
    }

    // Delete a car
    suspend fun delete(id: String): Document? = withContext(Dispatchers.IO) {
        collectionUsers.findOneAndDelete(Filters.eq("_id", ObjectId(id)))
    }
}


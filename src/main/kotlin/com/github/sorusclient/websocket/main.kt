package com.github.sorusclient.websocket

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

fun main() {
    embeddedServer(Netty, port = 8080) { main() }.start(wait = true)
}

private val users: MutableMap<WebSocketServerSession, User> = HashMap()
private val uuidToUser: MutableMap<String, User> = HashMap()

private val groups: MutableList<Group> = ArrayList()

fun Application.main() {
    install(WebSockets)
    routing {
        webSocket("/") {
            for(frame in incoming) {
                frame as? Frame.Text ?: continue

                val message = frame.readText()
                val id = message.substring(0, message.indexOf(" "))
                val json = message.substring(message.indexOf(" "))

                onReceiveMessage(this, id, JSONObject(json))
            }

            onDisconnect(this)
        }
    }
}

private suspend fun onReceiveMessage(webSocketServerSession: WebSocketServerSession, id: String, json: JSONObject) {
    when (id) {
        "authenticate" -> {
            val client = HttpClient()

            runBlocking {
                val sorusMlHash = "800D46DF17044D7033A983D9943E61CAA11835CB"

                val response = client.get<String>("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${json.get("username")}&serverId=$sorusMlHash")

                if (response.isNotEmpty() || System.getProperty("sorus.websocket.disableauth") == "true") {
                    val user = User(webSocketServerSession, json.getString("uuid"))
                    addUser(user)

                    sendMessage(webSocketServerSession, "connected")
                } else {
                    webSocketServerSession.close()
                }
            }
        }
        "createGroup" -> {
            val user = users[webSocketServerSession]!!
            val group = Group(user)
            groups.add(group)
            user.ownedGroup = group
            group.members.add(user)
        }
        "inviteToGroup" -> {
            val user = users[webSocketServerSession]!!
            val invitee = uuidToUser[json.getString("user")]

            if (invitee != null) {
                val jsonObject = JSONObject()
                jsonObject.put("inviter", user.uuid)
                sendMessage(invitee.socket, "acceptGroup", jsonObject)
            }
        }
        "acceptGroup" -> {
            val user = users[webSocketServerSession]!!
            val inviter = uuidToUser[json.getString("inviter")]!!

            for (groupMember in inviter.ownedGroup!!.members) {
                sendMessage(groupMember.socket, "addUserToGroup", JSONObject().apply {
                    put("user", user.uuid)
                })
            }

            for (groupMember in inviter.ownedGroup!!.members) {
                sendMessage(user.socket, "addUserToGroup", JSONObject().apply {
                    put("user", groupMember.uuid)
                })
            }

            inviter.ownedGroup!!.members.add(user)
        }
        "groupWarp" -> {
            val user = users[webSocketServerSession]!!
            val group = user.ownedGroup!!

            for (member in group.members) {
                if (member == user) continue

                sendMessage(member.socket, "groupWarp", JSONObject().apply {
                    put("ip", json.getString("ip"))
                })
            }
        }
    }
}

private fun addUser(user: User) {
    users[user.socket] = user
    uuidToUser[user.uuid] = user
}

private suspend fun sendMessage(socket: WebSocketServerSession, id: String, json: JSONObject = JSONObject()) {
    socket.send("$id $json")
}

private fun onDisconnect(webSocketServerSession: WebSocketServerSession) {
    val user = users.remove(webSocketServerSession)?.let { user ->
        user.ownedGroup?.let { disbandGroup(it) }
        uuidToUser.remove(user.uuid)
    }
}

private fun disbandGroup(group: Group) {
    groups.remove(group)
}
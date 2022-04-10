/*
 * Copyright (c) 2022. Danterus
 * Copyright (c) 2022. Sorus Contributors
 *
 * SPDX-License-Identifier: MPL-2.0
 */

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
import org.apache.commons.io.FileUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets

fun main() {
    embeddedServer(Netty, port = 8080) { main() }.start(wait = true)
}

private val users: MutableMap<WebSocketServerSession, User> = HashMap()
private val uuidToUser: MutableMap<String, User> = HashMap()

private val groups: MutableList<Group> = ArrayList()

//owner to invitees
private val groupInvites: MutableMap<User, MutableList<User>> = HashMap()

//user to people sending requests
private val friendRequests: MutableMap<User, MutableList<User>> = HashMap()

private val dataFile = File("data")
private val usersFile = File(dataFile, "users")

fun Application.main() {
    usersFile.mkdirs()

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
                    val user = createGetUser(json.getString("uuid"), webSocketServerSession)

                    sendMessage(webSocketServerSession, "connected")

                    for (friend in user.friends) {
                        sendMessage(webSocketServerSession, "addFriend", JSONObject().apply {
                            put("user", friend.uuid)
                        })
                        if (friend.socket != null) {
                            sendMessage(friend.socket!!, "requestUpdateStatus", JSONObject())
                        }
                    }
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
            user.group = group
            group.members.add(user)
        }
        "inviteToGroup" -> {
            val user = users[webSocketServerSession]!!
            val invitee = uuidToUser[json.getString("user")]

            if (invitee != null) {
                val jsonObject = JSONObject()
                jsonObject.put("inviter", user.uuid)
                groupInvites.computeIfAbsent(user) { ArrayList() }.add(invitee)
                sendMessage(invitee.socket!!, "acceptGroup", jsonObject)
            }
        }
        "acceptGroup" -> {
            val user = users[webSocketServerSession]!!
            val inviter = uuidToUser[json.getString("inviter")]!!

            if (groupInvites[inviter] != null && groupInvites[inviter]!!.contains(user)) {
                groupInvites[inviter]!!.remove(user)

                for (groupMember in inviter.ownedGroup!!.members) {
                    sendMessage(groupMember.socket!!, "addUserToGroup", JSONObject().apply {
                        put("user", user.uuid)
                    })
                }

                for (groupMember in inviter.ownedGroup!!.members) {
                    sendMessage(user.socket!!, "addUserToGroup", JSONObject().apply {
                        put("user", groupMember.uuid)
                    })
                }

                inviter.ownedGroup!!.members.add(user)
                user.group = inviter.ownedGroup!!
            }
        }
        "groupWarp" -> {
            val user = users[webSocketServerSession]!!
            val group = user.ownedGroup!!

            for (member in group.members) {
                if (member.uuid == user.uuid) continue

                sendMessage(member.socket!!, "groupWarp", JSONObject().apply {
                    put("ip", json.getString("ip"))
                })
            }
        }
        "sendFriend" -> {
            val user = users[webSocketServerSession]!!
            val wantedFriend = uuidToUser[json.getString("user")]

            if (wantedFriend != null) {
                friendRequests.computeIfAbsent(wantedFriend) { ArrayList() }.add(user)
                sendMessage(wantedFriend.socket!!, "friendRequest", JSONObject().apply {
                    put("user", user.uuid)
                })
            }
        }
        "acceptFriend" -> {
            val user = users[webSocketServerSession]!!
            val friender = uuidToUser[json.getString("user")]!!

            if (friendRequests[user] != null && friendRequests[user]!!.contains(friender)) {
                friendRequests[user]!!.remove(friender)

                user.friends.add(friender)
                friender.friends.add(user)

                saveUserToFile(user)
                saveUserToFile(friender)

                sendMessage(friender.socket!!, "addFriend", JSONObject().apply {
                    put("user", user.uuid)
                })

                sendMessage(friender.socket!!, "requestUpdateStatus", JSONObject())
                sendMessage(user.socket!!, "requestUpdateStatus", JSONObject())
            }
        }
        "updateStatus" -> {
            val user = users[webSocketServerSession]!!

            val version = json.getString("version")
            val action = json.getString("action")

            for (friend in user.friends) {
                if (friend.socket != null) {
                    sendMessage(friend.socket!!, "updateStatus", JSONObject().apply {
                        put("user", user.uuid)
                        put("version", version)
                        put("action", action)
                    })
                }
            }
        }
        "unfriend" -> {
            val user = users[webSocketServerSession]!!
            val removedFriend = uuidToUser[json.getString("user")]!!

            user.friends.remove(removedFriend)
            removedFriend.friends.remove(user)

            if (removedFriend.socket != null) {
                sendMessage(removedFriend.socket!!, "removeFriend", JSONObject().apply {
                    put("user", user.uuid)
                })
            }
        }
        "disbandGroup" -> {
            val user = users[webSocketServerSession]!!
            for (member in user.ownedGroup!!.members) {
                sendMessage(member.socket!!, "leaveGroup")
                member.group = null
            }

            groups.remove(user.ownedGroup)
            user.ownedGroup = null
        }
        "removeGroupMember" -> {
            val user = users[webSocketServerSession]!!
            val removedMember = uuidToUser[json.getString("user")]

            user.group!!.members.remove(removedMember)
            removedMember!!.group = null
            sendMessage(removedMember.socket!!, "leaveGroup")

            for (member in user.group!!.members) {
                sendMessage(member.socket!!, "removeGroupMember", JSONObject().apply {
                    put("user", json.getString("user"))
                })
            }
        }
        "updateInGameStatus" -> {
            val user = users[webSocketServerSession]!!
            val receiver = uuidToUser[json.getString("user")]

            if (receiver != null) {
                sendMessage(receiver.socket!!, "updateInGameStatus", JSONObject().apply {
                    put("user", user.uuid)
                })
            }
        }
    }
}

private suspend fun sendMessage(socket: WebSocketServerSession, id: String, json: JSONObject = JSONObject()) {
    println("${users[socket]!!.uuid} $id $json")
    socket.send("$id $json")
}

private fun onDisconnect(webSocketServerSession: WebSocketServerSession) {
    runBlocking {
        onReceiveMessage(webSocketServerSession, "updateStatus", JSONObject().apply {
            put("version", "")
            put("action", "offline")
        })
    }
    users.remove(webSocketServerSession)?.let { user ->
        user.ownedGroup?.let { disbandGroup(it) }
        user.socket = null
        if (user.group != null) {
            user.group!!.members.remove(user)
            user.group = null
        }
    }
}

private fun disbandGroup(group: Group) {
    groups.remove(group)
}

private fun saveUserToFile(user: User) {
    val file = File(usersFile, "${user.uuid}.json")
    val json = JSONObject().apply {
        put("friends", JSONArray()
            .apply {
                for (friend in user.friends) {
                    put(friend.uuid)
                }
            })
    }

    FileUtils.writeStringToFile(file, json.toString(2), StandardCharsets.UTF_8)
}

private fun createGetUser(uuid: String, socket: WebSocketServerSession? = null): User {
    var userMain: User? = null
    for (user in users) {
        if (user.value.uuid == uuid) {
            userMain = user.value
            uuidToUser[uuid] = user.value
            break
        }
    }

    if (userMain == null) {
        for (user in uuidToUser) {
            if (user.key == uuid) {
                userMain = user.value
                break
            }
        }

        if (userMain == null) {
            userMain = User(socket, uuid, friends = ArrayList())
            uuidToUser[uuid] = userMain

            if (socket != null) {
                users[socket] = userMain
            }

            loadUserFromFile(userMain)
        }
    }

    if (socket != null) {
        users[socket] = userMain
        userMain.socket = socket
    }

    return userMain
}

private fun loadUserFromFile(user: User) {
    val file = File(usersFile, "${user.uuid}.json")
    if (!file.exists()) return

    val json = JSONObject(FileUtils.readFileToString(file, StandardCharsets.UTF_8))

    if (json.has("friends")) {
        val friends = json.getJSONArray("friends")
        for (friend in friends) {
            user.friends.add(createGetUser(friend.toString()))
        }
    }
}
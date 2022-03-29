package com.github.sorusclient.websocket

import io.ktor.websocket.*

data class User(var socket: WebSocketServerSession?, var uuid: String, var ownedGroup: Group? = null, var friends: MutableList<User>)
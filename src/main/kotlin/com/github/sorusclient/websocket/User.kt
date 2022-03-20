package com.github.sorusclient.websocket

import io.ktor.websocket.*

data class User(val socket: WebSocketServerSession, var uuid: String, var ownedGroup: Group? = null)
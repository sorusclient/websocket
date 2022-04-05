/*
 * Copyright (c) 2022. Danterus
 * Copyright (c) 2022. Sorus Contributors
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package com.github.sorusclient.websocket

import io.ktor.websocket.*

data class User(var socket: WebSocketServerSession?, var uuid: String, var ownedGroup: Group? = null, var group: Group? = null, var friends: MutableList<User>) {

    override fun hashCode(): Int {
        return uuid.hashCode()
    }

}
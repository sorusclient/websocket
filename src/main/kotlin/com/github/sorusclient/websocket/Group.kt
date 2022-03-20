package com.github.sorusclient.websocket

data class Group(val owner: User, val members: MutableList<User> = ArrayList()) {
}
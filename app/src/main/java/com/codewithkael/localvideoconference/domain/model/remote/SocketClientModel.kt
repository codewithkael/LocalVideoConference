package com.codewithkael.localvideoconference.domain.model.remote

import org.java_websocket.WebSocket


data class SocketClientModel(
    val name:String,
    val connection: WebSocket
)

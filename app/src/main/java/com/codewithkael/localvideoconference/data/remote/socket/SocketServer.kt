package com.codewithkael.localvideoconference.data.remote.socket

import com.codewithkael.localvideoconference.domain.model.remote.DataModel
import com.codewithkael.localvideoconference.domain.model.remote.DataModelType
import com.codewithkael.localvideoconference.domain.model.remote.SocketClientModel
import com.google.gson.Gson
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

class SocketServer(
    private val gson: Gson
) {

    private var socketserver: WebSocketServer? = null
    private val clients: MutableList<SocketClientModel> = mutableListOf()
    private val socketClients: MutableList<WebSocket> = mutableListOf()

    fun init(
        port: Int,
        listener: IncomingSocketServerMessage,
        onStartServer: () -> Unit,
        onError: () -> Unit
    ) {
        if (socketserver == null) {
            socketserver = object : WebSocketServer(InetSocketAddress(port)) {
                override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
                    conn?.let { socketClients.add(it) }
                }

                override fun onClose(
                    conn: WebSocket?, code: Int, reason: String?, remote: Boolean
                ) {
                    conn?.let {
                        socketClients.remove(it)
                        removeTimeOutedConnection(it, listener)
                    }
                }

                override fun onMessage(conn: WebSocket?, message: String?) {
                    val model = runCatching {
                        gson.fromJson(message, DataModel::class.java)
                    }
                    model.onSuccess { data ->
                        if (data.type == DataModelType.SignIn) {
                            clients.add(SocketClientModel(data.username, conn!!))
                        }
                        listener.onNewMessage(data)
                    }
                }

                override fun onError(conn: WebSocket?, ex: Exception?) {
                    if (ex?.message == "Address already in use") {
                        onError.invoke()
                    }
                    ex?.printStackTrace()
                    conn?.let {
                        socketClients.remove(it)
                        removeTimeOutedConnection(it, listener)
                    }
                }

                override fun onStart() {
                    onStartServer.invoke()
                }


            }.apply { start() }
        }
    }

    private fun removeTimeOutedConnection(conn: WebSocket, listener: IncomingSocketServerMessage) {
        runCatching {
            val userToRemove = clients.find { it.connection == conn }
            userToRemove?.let {
                listener.connectionTimedOut(it.name)
                clients.remove(it)
            }
        }

    }

    fun sendDataToClient(dataModel: DataModel) {
        runCatching {
            val userToSend = clients.find { it.name == dataModel.target }
            userToSend?.let {
                val jsonModel = gson.toJson(dataModel)
                it.connection.send(
                    jsonModel
                )
            }
        }
    }

    fun onDestroy() = runCatching {
        socketserver?.stop()
        socketserver = null
    }
}
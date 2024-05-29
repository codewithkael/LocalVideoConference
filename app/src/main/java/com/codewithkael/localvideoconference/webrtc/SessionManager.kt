package com.codewithkael.localvideoconference.webrtc

import android.content.Context
import android.util.Log
import com.codewithkael.localvideoconference.di.MyApplication
import com.codewithkael.localvideoconference.model.remote.MessageModel
import com.codewithkael.localvideoconference.model.remote.SocketEvents.Answer
import com.codewithkael.localvideoconference.model.remote.SocketEvents.Ice
import com.codewithkael.localvideoconference.remote.socket.IncomingSocketServerMessage
import com.codewithkael.localvideoconference.remote.socket.SocketServer
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import javax.inject.Inject

class SessionManager @Inject constructor(
    private val webRTCFactory: WebRTCFactory,
    private val gson: Gson,
    private val context: Context
) : IncomingSocketServerMessage, LocalStreamListener {
    private val TAG = "WebRTCManager"

    private val socketServer = SocketServer(gson)
    private var port = 3013

    //variables
    private val connections = mutableMapOf<String, RTCClient>()
    private var socketJob: Job? = null

    //state
    val mediaStreamsState: MutableStateFlow<HashMap<String, MediaStream>> = MutableStateFlow(
        hashMapOf()
    )

    fun start(
        surfaceViewRenderer: SurfaceViewRenderer
    ) {
        webRTCFactory.init(surfaceViewRenderer, this)
        initSocketServer()
    }

    private var listener: ManagerEventListener? = null
    fun setListener(listener: ManagerEventListener) {
        this.listener = listener
    }

    private fun initSocketServer() {
        socketJob?.cancel()
        socketJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "initSocketServer: started with port $port")
            socketServer.init(port, this@SessionManager, onStartServer = {
                listener?.onSocketPortConnected(port)

            }) {
                socketServer.onDestroy()
                port++
                initSocketServer()
                return@init
            }
        }
    }

    fun onDestroy() {
        webRTCFactory.onDestroy()
        socketServer.onDestroy()
        this.listener = null
    }

    override fun onNewMessage(message: MessageModel) {
        Log.d(TAG, "onNewMessage: $message")
        when (message.type) {
//            SignIn -> handleSignIn(message)
            Answer -> handleAnswer(message)
            Ice -> handleIceCandidates(message)
            else -> Unit
        }
    }

    private fun handleIceCandidates(message: MessageModel) {
        val ice = runCatching {
            gson.fromJson(message.data.toString(), IceCandidate::class.java)
        }
        Log.d(TAG, "handleIceCandidates: $ice")
        ice.onSuccess {
            findClient(message.username!!).apply {
                Log.d(TAG, "handleIceCandidates: $this")
                this?.addIceCandidateToPeer(it)
            }
        }
    }

    private fun findClient(username: String): RTCClient? {
        return connections[username]
    }

    private fun handleSignIn(message: MessageModel) {
        CoroutineScope(Dispatchers.Default).launch {
            val connection = webRTCFactory.createRtcClient(object : MyPeerObserver() {

                override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
                    super.onAddTrack(p0, p1)
                    Log.d(TAG, "onAddTrack: ${p1?.toList()}")
                }

                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    findClient(message.username!!).apply {
                        Log.d(TAG, "onIceCandidate: generated $this")
                        if (p0 != null) {
                            this?.addIceCandidateToPeer(p0)
                            Log.d(TAG, "onIceCandidate: generated2 $p0")

                        }
                    }
                    socketServer.sendDataToClient(
                        MessageModel(
                            type = Ice,
                            username = "server",
                            target = message.username,
                            data = gson.toJson(p0)
                        )
                    )
                }

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    super.onConnectionChange(newState)
                    Log.d(TAG, "onConnectionChange: new state$newState")
                }
            }, message.username!!,
                object : WebRTCSignalListener {
                    override fun onTransferEventToSocket(data: MessageModel) {
                        socketServer.sendDataToClient(data)
                    }
                })
            connection?.let {
                connections[message.username] = it
                connection.call()
                Log.d(TAG, "handleSignIng: ${connections.toList()}")
            }
        }
    }

    private fun handleAnswer(message: MessageModel) {
        findClient(message.username!!).apply {
            this?.onRemoteSessionReceived(
                SessionDescription(
                    SessionDescription.Type.ANSWER,
                    message.data.toString()
                )
            )
        }
    }

    override fun connectionTimedOut(username: String) {
        runCatching {
            findClient(username)?.onDestroy()
            connections.remove(username)
            Log.d(TAG, "handleRemove: ${connections.toList()}")

        }
    }

    override fun onLocalStreamReady(mediaStream: MediaStream) {
        addMediaStreamToState(MyApplication.username, mediaStream)
    }

    private fun getMediaStreams() = mediaStreamsState.value
    fun addMediaStreamToState(username: String, mediaStream: MediaStream) {
        val updatedData = HashMap(getMediaStreams()).apply {
            put(username, mediaStream)
        }
        mediaStreamsState.value = updatedData
    }

    fun removeMediaStreamFromState(username: String) {
        val updatedData = HashMap(getMediaStreams()).apply {
            remove(username)
        }
        // Update the state with the new HashMap
        mediaStreamsState.value = updatedData
    }

}
package com.codewithkael.localvideoconference.webrtc

import com.codewithkael.localvideoconference.model.remote.MessageModel


interface WebRTCSignalListener {
    fun onTransferEventToSocket(data: MessageModel)

}
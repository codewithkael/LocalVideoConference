package com.codewithkael.localvideoconference.webrtc

import org.webrtc.MediaStream

interface LocalStreamListener {
    fun onLocalStreamReady(mediaStream: MediaStream)
}
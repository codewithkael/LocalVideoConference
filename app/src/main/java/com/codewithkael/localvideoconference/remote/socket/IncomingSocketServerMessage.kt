package com.codewithkael.localvideoconference.remote.socket

import com.codewithkael.localvideoconference.model.remote.MessageModel

interface IncomingSocketServerMessage {
    fun onNewMessage(message: MessageModel)
    fun connectionTimedOut(username:String)

}

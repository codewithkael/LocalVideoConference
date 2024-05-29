package com.codewithkael.localvideoconference.data.remote.socket

import com.codewithkael.localvideoconference.domain.model.remote.DataModel

interface IncomingSocketServerMessage {
    fun onNewMessage(message: DataModel)
    fun connectionTimedOut(username:String)

}

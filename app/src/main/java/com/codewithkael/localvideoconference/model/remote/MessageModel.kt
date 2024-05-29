package com.codewithkael.localvideoconference.model.remote

data class MessageModel(
     val type: SocketEvents,
     val username: String? = null,
     val target: String? = null,
     val data:Any?=null
)

enum class SocketEvents {
     StoreUser,CreateRoom,JoinRoom,RoomStatus,LeaveAllRooms,
     Offer, Answer, Ice,NewSession,StartCall
}

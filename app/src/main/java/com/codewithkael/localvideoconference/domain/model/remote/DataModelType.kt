package com.codewithkael.localvideoconference.domain.model.remote

enum class DataModelType{
    SignIn, Offer, Answer, IceCandidates
}


data class DataModel(
    val type:DataModelType?=null,
    val username:String,
    val target:String?=null,
    val data:Any?=null
)

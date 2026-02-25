package com.wearbubbles.api

import com.wearbubbles.api.dto.*
import retrofit2.http.*

interface BlueBubblesApi {

    @GET("/api/v1/ping")
    suspend fun ping(@Query("password") password: String): PingResponse

    @POST("/api/v1/chat/query")
    suspend fun getChats(
        @Query("password") password: String,
        @Body body: ChatQueryRequest
    ): ChatQueryResponse

    @POST("/api/v1/message/query")
    suspend fun getMessages(
        @Query("password") password: String,
        @Body body: MessageQueryRequest
    ): MessageQueryResponse

    @POST("/api/v1/message/text")
    suspend fun sendMessage(
        @Query("password") password: String,
        @Body body: SendMessageRequest
    ): SendMessageResponse

    @POST("/api/v1/message/react")
    suspend fun reactToMessage(
        @Query("password") password: String,
        @Body body: ReactRequest
    ): SendMessageResponse

    @POST("/api/v1/chat/{guid}/read")
    suspend fun markChatRead(
        @Path("guid") chatGuid: String,
        @Query("password") password: String
    ): BasicResponse

    @GET("/api/v1/contact")
    suspend fun getContacts(
        @Query("password") password: String
    ): ContactResponse
}

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

    @GET("/api/v1/chat/{guid}/message")
    suspend fun getMessages(
        @Path("guid") chatGuid: String,
        @Query("password") password: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 15,
        @Query("sort") sort: String = "DESC",
        @Query("with[]") with: List<String> = listOf("chat", "handle", "attachment")
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

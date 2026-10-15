package com.example.twitchlite.data.remote.api

import com.example.twitchlite.data.remote.dto.FollowedStreamsResponse
import com.example.twitchlite.data.remote.dto.HelixUsersResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface TwitchHelixApi {
    @GET("helix/streams/followed")
    suspend fun getFollowedStreams(
        @Header("Authorization") bearerToken: String, // Formato: "Bearer <token>"
        @Header("Client-Id") clientId: String,
        @Query("user_id") userId: String,
        @Query("first") pageSize: Int = 20,
        @Query("after") afterCursor: String? = null
    ): FollowedStreamsResponse

    @GET("helix/users")
    suspend fun getCurrentUser(
        @Header("Authorization") bearerToken: String,
        @Header("Client-Id") clientId: String
    ): HelixUsersResponse
}
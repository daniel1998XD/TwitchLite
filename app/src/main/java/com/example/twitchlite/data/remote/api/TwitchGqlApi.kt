package com.example.twitchlite.data.remote.api

import com.example.twitchlite.data.remote.dto.ChannelPointsContextResponse
import com.example.twitchlite.data.remote.dto.DropsInventoryResponse
import com.example.twitchlite.data.remote.dto.GqlGenericRequest
import com.example.twitchlite.data.remote.dto.GqlPlaybackTokenRequest
import com.example.twitchlite.data.remote.dto.GqlPlaybackTokenResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface TwitchGqlApi {
    @Headers(
        "Client-ID: kimne78kx3ncx6brgo4mv6wki5h1ko",
        "Origin: https://player.twitch.tv",
        "Referer: https://player.twitch.tv",
        "Content-Type: application/json"
    )
    @POST("gql")
    suspend fun getPlaybackAccessToken(
        @Header("Authorization") oauthHeader: String? = null,
        @Body request: GqlPlaybackTokenRequest
    ): GqlPlaybackTokenResponse

    @Headers("Content-Type: application/json")
    @POST("gql")
    suspend fun queryChannelPointsContext(
        @Header("Authorization") oauthHeader: String,
        @Header("Client-ID") clientId: String,
        @Body request: GqlGenericRequest
    ): ChannelPointsContextResponse

    @Headers("Content-Type: application/json")
    @POST("gql")
    suspend fun queryDropsInventory(
        @Header("Authorization") oauthHeader: String,
        @Header("Client-ID") clientId: String,
        @Body request: GqlGenericRequest
    ): DropsInventoryResponse

    @Headers("Content-Type: application/json")
    @POST("gql")
    suspend fun executeAutoClaimMutation(
        @Header("Authorization") oauthHeader: String,
        @Header("Client-ID") clientId: String,
        @Body request: GqlGenericRequest
    ): Any
}
package com.example.twitchlite.data.remote.api

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
        @Header("Authorization") oauthHeader: String?, // Formato: "OAuth <token>" ou null
        @Body request: GqlPlaybackTokenRequest
    ): GqlPlaybackTokenResponse
}
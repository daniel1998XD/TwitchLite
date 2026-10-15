package com.example.twitchlite.data.remote.api

import com.example.twitchlite.data.remote.dto.TokenRefreshResponse
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface TwitchAuthRefreshApi {
    @FormUrlEncoded
    @POST("oauth2/token")
    fun refreshTokenSync(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): Call<TokenRefreshResponse>
}
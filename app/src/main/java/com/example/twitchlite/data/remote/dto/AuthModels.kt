package com.example.twitchlite.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TokenRefreshResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String?
)
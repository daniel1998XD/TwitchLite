package com.example.twitchlite.data.remote.api

import com.example.twitchlite.data.remote.dto.BttvChannelResponse
import com.example.twitchlite.data.remote.dto.BttvEmoteDto
import com.example.twitchlite.data.remote.dto.FfzResponse
import com.example.twitchlite.data.remote.dto.SevenTvEmoteSetResponse
import com.example.twitchlite.data.remote.dto.SevenTvUserResponse
import retrofit2.http.GET
import retrofit2.http.Url

interface ThirdPartyEmoteApi {
    @GET
    suspend fun getSevenTvGlobal(@Url url: String = "https://7tv.io/v3/emote-sets/global"): SevenTvEmoteSetResponse

    @GET
    suspend fun getSevenTvChannel(@Url url: String): SevenTvUserResponse

    @GET
    suspend fun getBttvGlobal(@Url url: String = "https://api.betterttv.net/3/cached/emotes/global"): List<BttvEmoteDto>

    @GET
    suspend fun getBttvChannel(@Url url: String): BttvChannelResponse

    @GET
    suspend fun getFfzGlobal(@Url url: String = "https://api.frankerfacez.com/v1/set/global"): FfzResponse

    @GET
    suspend fun getFfzChannel(@Url url: String): FfzResponse
}
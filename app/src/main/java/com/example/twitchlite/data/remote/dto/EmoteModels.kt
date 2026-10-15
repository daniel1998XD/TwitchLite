package com.example.twitchlite.data.remote.dto

import com.google.gson.annotations.SerializedName

// ==================== 7TV DTOs ====================
data class SevenTvEmoteSetResponse(
    @SerializedName("emotes") val emotes: List<SevenTvEmoteDto>?
)

data class SevenTvUserResponse(
    @SerializedName("emote_set") val emoteSet: SevenTvEmoteSetResponse?
)

data class SevenTvEmoteDto(
    @SerializedName("name") val name: String,
    @SerializedName("data") val data: SevenTvEmoteDataDto?
)

data class SevenTvEmoteDataDto(
    @SerializedName("host") val host: SevenTvHostDto?
)

data class SevenTvHostDto(
    @SerializedName("url") val url: String?
)

// ==================== BetterTTV (BTTV) DTOs ====================
data class BttvEmoteDto(
    @SerializedName("id") val id: String,
    @SerializedName("code") val code: String
)

data class BttvChannelResponse(
    @SerializedName("channelEmotes") val channelEmotes: List<BttvEmoteDto>?,
    @SerializedName("sharedEmotes") val sharedEmotes: List<BttvEmoteDto>?
)

// ==================== FrankerFaceZ (FFZ) DTOs ====================
data class FfzResponse(
    @SerializedName("sets") val sets: Map<String, FfzEmoteSetDto>?
)

data class FfzEmoteSetDto(
    @SerializedName("emoticons") val emoticons: List<FfzEmoteDto>?
)

data class FfzEmoteDto(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)
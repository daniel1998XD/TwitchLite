package com.example.twitchlite.data.remote.dto

import com.google.gson.annotations.SerializedName

data class FollowedStreamsResponse(
    @SerializedName("data") val streams: List<HelixStreamDto>,
    @SerializedName("pagination") val pagination: HelixPagination?
)

data class HelixPagination(
    @SerializedName("cursor") val cursor: String?
)

data class HelixStreamDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("user_login") val userLogin: String,
    @SerializedName("user_name") val userName: String,
    @SerializedName("game_name") val gameName: String,
    @SerializedName("title") val title: String,
    @SerializedName("viewer_count") val viewerCount: Int,
    @SerializedName("thumbnail_url") val thumbnailUrl: String
) {
    fun getFormattedThumbnail(width: Int = 440, height: Int = 248): String {
        return thumbnailUrl
            .replace("{width}", width.toString())
            .replace("{height}", height.toString())
    }
}

data class HelixUsersResponse(
    @SerializedName("data") val users: List<HelixUserDto>
)

data class HelixUserDto(
    @SerializedName("id") val id: String,
    @SerializedName("login") val login: String,
    @SerializedName("display_name") val displayName: String
)
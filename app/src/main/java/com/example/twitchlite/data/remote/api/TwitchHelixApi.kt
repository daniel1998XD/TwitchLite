package com.example.twitchlite.data.remote.api

import com.example.twitchlite.data.remote.dto.FollowedStreamsResponse
import com.example.twitchlite.data.remote.dto.HelixUsersResponse
import com.google.gson.annotations.SerializedName
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

    @GET("helix/users")
    suspend fun getUserByLogin(
        @Header("Authorization") bearerToken: String,
        @Header("Client-Id") clientId: String,
        @Query("login") login: String
    ): HelixUsersResponse

    @GET("helix/videos")
    suspend fun getChannelVideos(
        @Header("Authorization") bearerToken: String,
        @Header("Client-Id") clientId: String,
        @Query("user_id") userId: String,
        @Query("first") first: Int = 20,
        @Query("after") afterCursor: String? = null,
        @Query("sort") sort: String = "time", // Ordenado por data (mais recentes primeiro)
        @Query("period") period: String = "all", // "day", "week", "month", "all"
        @Query("type") type: String = "all"
    ): HelixVideosResponse

    @GET("helix/clips")
    suspend fun getChannelClips(
        @Header("Authorization") bearerToken: String,
        @Header("Client-Id") clientId: String,
        @Query("broadcaster_id") broadcasterId: String,
        @Query("first") first: Int = 20,
        @Query("after") afterCursor: String? = null,
        @Query("started_at") startedAt: String? = null, // RFC3339 UTC
        @Query("ended_at") endedAt: String? = null      // RFC3339 UTC
    ): HelixClipsResponse
}

// ==================== DTOs HELIX PARA VODS (/videos) E CLIPS (/clips) COM PAGINAÇÃO ====================

data class HelixPaginationDto(
    @SerializedName("cursor") val cursor: String? = null
)

data class HelixVideosResponse(
    @SerializedName("data") val videos: List<HelixVideoDto> = emptyList(),
    @SerializedName("pagination") val pagination: HelixPaginationDto? = null
)

data class HelixVideoDto(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("user_login") val userLogin: String?,
    @SerializedName("user_name") val userName: String?,
    @SerializedName("title") val title: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("duration") val duration: String = ""
) {
    fun getFormattedThumbnail(width: Int = 440, height: Int = 248): String {
        val raw = thumbnailUrl.orEmpty()
        if (raw.isBlank()) return ""
        return raw
            .replace("%{width}", width.toString())
            .replace("%{height}", height.toString())
            .replace("{width}", width.toString())
            .replace("{height}", height.toString())
    }
}

data class HelixClipsResponse(
    @SerializedName("data") val clips: List<HelixClipDto> = emptyList(),
    @SerializedName("pagination") val pagination: HelixPaginationDto? = null
)

data class HelixClipDto(
    @SerializedName("id") val id: String,
    @SerializedName("url") val url: String?,
    @SerializedName("broadcaster_name") val broadcasterName: String?,
    @SerializedName("creator_name") val creatorName: String?,
    @SerializedName("title") val title: String,
    @SerializedName("view_count") val viewCount: Int = 0,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?,
    @SerializedName("duration") val duration: Float = 0f
)
package com.example.twitchlite.data.remote.api

import com.example.twitchlite.data.remote.dto.GqlCategoryStreamsRequest
import com.example.twitchlite.data.remote.dto.GqlCategoryStreamsResponse
import com.example.twitchlite.data.remote.dto.GqlChannelVideosAndClipsRequest
import com.example.twitchlite.data.remote.dto.GqlChannelVideosAndClipsResponse
import com.example.twitchlite.data.remote.dto.GqlClipPlaybackRequest
import com.example.twitchlite.data.remote.dto.GqlClipPlaybackResponse
import com.example.twitchlite.data.remote.dto.GqlFollowedGamesRequest
import com.example.twitchlite.data.remote.dto.GqlFollowedGamesResponse
import com.example.twitchlite.data.remote.dto.GqlGameLookupRequest
import com.example.twitchlite.data.remote.dto.GqlGameLookupResponse
import com.example.twitchlite.data.remote.dto.GqlPlaybackTokenRequest
import com.example.twitchlite.data.remote.dto.GqlPlaybackTokenResponse
import com.example.twitchlite.data.remote.dto.GqlSearchCategoriesRequest
import com.example.twitchlite.data.remote.dto.GqlSearchCategoriesResponse
import com.example.twitchlite.data.remote.dto.GqlSearchUsersRequest
import com.example.twitchlite.data.remote.dto.GqlSearchUsersResponse
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

    @Headers(
        "Client-ID: kimne78kx3ncx6brgo4mv6wki5h1ko",
        "Content-Type: application/json"
    )
    @POST("gql")
    suspend fun queryFollowedGames(
        @Header("Authorization") oauthHeader: String? = null,
        @Body request: GqlFollowedGamesRequest
    ): GqlFollowedGamesResponse

    @Headers(
        "Client-ID: kimne78kx3ncx6brgo4mv6wki5h1ko",
        "Content-Type: application/json"
    )
    @POST("gql")
    suspend fun queryGameByName(
        @Header("Authorization") oauthHeader: String? = null,
        @Body request: GqlGameLookupRequest
    ): GqlGameLookupResponse

    @Headers(
        "Client-ID: kimne78kx3ncx6brgo4mv6wki5h1ko",
        "Content-Type: application/json"
    )
    @POST("gql")
    suspend fun queryCategoryStreams(
        @Header("Authorization") oauthHeader: String? = null,
        @Body request: GqlCategoryStreamsRequest
    ): GqlCategoryStreamsResponse

    @Headers(
        "Client-ID: kimne78kx3ncx6brgo4mv6wki5h1ko",
        "Content-Type: application/json"
    )
    @POST("gql")
    suspend fun querySearchUsers(
        @Header("Authorization") oauthHeader: String? = null,
        @Body request: GqlSearchUsersRequest
    ): GqlSearchUsersResponse

    @Headers(
        "Client-ID: kimne78kx3ncx6brgo4mv6wki5h1ko",
        "Content-Type: application/json"
    )
    @POST("gql")
    suspend fun querySearchCategories(
        @Header("Authorization") oauthHeader: String? = null,
        @Body request: GqlSearchCategoriesRequest
    ): GqlSearchCategoriesResponse

    @Headers(
        "Client-ID: kimne78kx3ncx6brgo4mv6wki5h1ko",
        "Content-Type: application/json"
    )
    @POST("gql")
    suspend fun queryClipPlayback(
        @Header("Authorization") oauthHeader: String? = null,
        @Body request: GqlClipPlaybackRequest
    ): GqlClipPlaybackResponse

    @Headers(
        "Client-ID: kimne78kx3ncx6brgo4mv6wki5h1ko",
        "Content-Type: application/json"
    )
    @POST("gql")
    suspend fun queryChannelVideosAndClips(
        @Header("Authorization") oauthHeader: String? = null,
        @Body request: GqlChannelVideosAndClipsRequest
    ): GqlChannelVideosAndClipsResponse
}
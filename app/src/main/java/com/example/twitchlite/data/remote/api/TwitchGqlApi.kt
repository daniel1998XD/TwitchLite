package com.example.twitchlite.data.remote.api

import com.example.twitchlite.data.remote.dto.ChannelPointsContextResponse
import com.example.twitchlite.data.remote.dto.DropsInventoryResponse
import com.example.twitchlite.data.remote.dto.GqlCategoryStreamsRequest
import com.example.twitchlite.data.remote.dto.GqlCategoryStreamsResponse
import com.example.twitchlite.data.remote.dto.GqlFollowedGamesRequest
import com.example.twitchlite.data.remote.dto.GqlFollowedGamesResponse
import com.example.twitchlite.data.remote.dto.GqlGameLookupRequest
import com.example.twitchlite.data.remote.dto.GqlGameLookupResponse
import com.example.twitchlite.data.remote.dto.GqlGenericRequest
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
}
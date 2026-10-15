package com.example.twitchlite.data.remote.dto

import com.google.gson.annotations.SerializedName

data class GqlPlaybackTokenRequest(
    @SerializedName("operationName") val operationName: String = "PlaybackAccessToken_Template",
    @SerializedName("query") val query: String = PLAYBACK_TOKEN_QUERY,
    @SerializedName("variables") val variables: GqlVariables
) {
    companion object {
        const val PLAYBACK_TOKEN_QUERY = """
            query PlaybackAccessToken_Template(${'$'}login: String!, ${'$'}isLive: Boolean!, ${'$'}vodID: ID!, ${'$'}isVod: Boolean!, ${'$'}playerType: String!, ${'$'}platform: String!, ${'$'}playerBackend: String!) {
              streamPlaybackAccessToken(channelName: ${'$'}login, params: {platform: ${'$'}platform, playerBackend: ${'$'}playerBackend, playerType: ${'$'}playerType}) @include(if: ${'$'}isLive) {
                value
                signature
                authorization {
                  isForbidden
                  forbiddenReasonCode
                }
              }
              videoPlaybackAccessToken(id: ${'$'}vodID, params: {platform: ${'$'}platform, playerBackend: ${'$'}playerBackend, playerType: ${'$'}playerType}) @include(if: ${'$'}isVod) {
                value
                signature
              }
            }
        """
    }
}

data class GqlVariables(
    @SerializedName("isLive") val isLive: Boolean = true,
    @SerializedName("login") val login: String,
    @SerializedName("isVod") val isVod: Boolean = false,
    @SerializedName("vodID") val vodId: String = "",
    @SerializedName("playerType") val playerType: String = "embed",
    @SerializedName("platform") val platform: String = "web",
    @SerializedName("playerBackend") val playerBackend: String = "mediaplayer"
)

data class GqlPlaybackTokenResponse(
    @SerializedName("data") val data: GqlData?
)

data class GqlData(
    @SerializedName("streamPlaybackAccessToken") val token: StreamPlaybackAccessToken?
)

data class StreamPlaybackAccessToken(
    @SerializedName("value") val value: String,
    @SerializedName("signature") val signature: String
)

// ==================== MODELOS GQL PARA AUTO-CLAIM (PONTOS E DROPS) ====================

data class GqlGenericRequest(
    @SerializedName("operationName") val operationName: String,
    @SerializedName("variables") val variables: Map<String, Any>,
    @SerializedName("extensions") val extensions: GqlExtensions
)

data class GqlExtensions(
    @SerializedName("persistedQuery") val persistedQuery: GqlPersistedQuery
)

data class GqlPersistedQuery(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("sha256Hash") val sha256Hash: String
)

data class ChannelPointsContextResponse(
    @SerializedName("data") val data: ChannelPointsData?
)

data class ChannelPointsData(
    @SerializedName("community") val community: CommunityChannel?
)

data class CommunityChannel(
    @SerializedName("id") val channelId: String?,
    @SerializedName("channel") val channel: ChannelSelfData?
)

data class ChannelSelfData(
    @SerializedName("self") val self: ChannelPointsSelf?
)

data class ChannelPointsSelf(
    @SerializedName("communityPoints") val communityPoints: CommunityPointsAvailable?
)

data class CommunityPointsAvailable(
    @SerializedName("availableClaim") val availableClaim: AvailableClaimDto?
)

data class AvailableClaimDto(
    @SerializedName("id") val claimId: String
)

data class DropsInventoryResponse(
    @SerializedName("data") val data: DropsUserData?
)

data class DropsUserData(
    @SerializedName("currentUser") val currentUser: DropsInventoryUser?
)

data class DropsInventoryUser(
    @SerializedName("inventory") val inventory: DropsInventoryDto?
)

data class DropsInventoryDto(
    @SerializedName("dropCampaignsInProgress") val campaigns: List<DropCampaignDto>?
)

data class DropCampaignDto(
    @SerializedName("timeBasedDrops") val timeBasedDrops: List<TimeBasedDropDto>?
)

data class TimeBasedDropDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String?,
    @SerializedName("self") val self: TimeBasedDropSelfDto?
)

data class TimeBasedDropSelfDto(
    @SerializedName("dropInstanceID") val dropInstanceId: String?,
    @SerializedName("isClaimed") val isClaimed: Boolean = false,
    @SerializedName("currentMinutesWatched") val currentMinutesWatched: Int = 0,
    @SerializedName("requiredMinutesWatched") val requiredMinutesWatched: Int = 1
)
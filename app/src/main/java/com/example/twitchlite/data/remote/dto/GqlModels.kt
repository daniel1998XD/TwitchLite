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

// ==================== MODELOS GQL PARA CATEGORIAS SEGUIDAS ====================

data class GqlFollowedGamesRequest(
    @SerializedName("operationName") val operationName: String = "FollowedGames_Query",
    @SerializedName("query") val query: String = FOLLOWED_GAMES_QUERY,
    @SerializedName("variables") val variables: GqlFollowedGamesVariables
) {
    companion object {
        const val FOLLOWED_GAMES_QUERY = """
            query FollowedGames_Query(${'$'}login: String!, ${'$'}first: Int!) {
              currentUser {
                followedGames(first: ${'$'}first, type: ALL) {
                  nodes {
                    id
                    name
                    displayName
                    boxArtURL(width: 285, height: 380)
                    viewersCount
                    broadcastersCount
                  }
                }
              }
              user(login: ${'$'}login) {
                followedGames(first: ${'$'}first, type: ALL) {
                  nodes {
                    id
                    name
                    displayName
                    boxArtURL(width: 285, height: 380)
                    viewersCount
                    broadcastersCount
                  }
                }
              }
            }
        """
    }
}

data class GqlFollowedGamesVariables(
    @SerializedName("login") val login: String,
    @SerializedName("first") val first: Int = 30
)

data class GqlFollowedGamesResponse(
    @SerializedName("data") val data: GqlFollowedGamesData?
)

data class GqlFollowedGamesData(
    @SerializedName("currentUser") val currentUser: GqlUserFollowedGamesWrapper?,
    @SerializedName("user") val user: GqlUserFollowedGamesWrapper?
)

data class GqlUserFollowedGamesWrapper(
    @SerializedName("followedGames") val followedGames: GqlFollowedGamesConnection?
)

data class GqlFollowedGamesConnection(
    @SerializedName("nodes") val nodes: List<GqlGameNode>?
)

data class GqlGameNode(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("boxArtURL") val boxArtUrl: String?,
    @SerializedName("viewersCount") val viewersCount: Int?,
    @SerializedName("broadcastersCount") val broadcastersCount: Int?
) {
    fun getFormattedBoxArtUrl(width: Int = 285, height: Int = 380): String {
        val raw = boxArtUrl.orEmpty()
        return if (raw.isNotBlank()) {
            raw.replace("{width}", width.toString())
                .replace("{height}", height.toString())
        } else {
            "https://static-cdn.jtvnw.net/ttv-boxart/${id}-${width}x${height}.jpg"
        }
    }
}

data class GqlGameLookupRequest(
    @SerializedName("operationName") val operationName: String = "GameLookup_Query",
    @SerializedName("query") val query: String = GAME_LOOKUP_QUERY,
    @SerializedName("variables") val variables: Map<String, String>
) {
    companion object {
        const val GAME_LOOKUP_QUERY = """
            query GameLookup_Query(${'$'}name: String!) {
              game(name: ${'$'}name) {
                id
                name
                displayName
                boxArtURL(width: 285, height: 380)
                viewersCount
                broadcastersCount
              }
            }
        """
    }
}

data class GqlGameLookupResponse(
    @SerializedName("data") val data: GqlGameLookupData?
)

data class GqlGameLookupData(
    @SerializedName("game") val game: GqlGameNode?
)

// ==================== MODELOS GQL PARA LIVES DE UMA CATEGORIA ====================

data class GqlCategoryStreamsRequest(
    @SerializedName("operationName") val operationName: String = "CategoryStreams_Query",
    @SerializedName("query") val query: String = CATEGORY_STREAMS_QUERY,
    @SerializedName("variables") val variables: GqlCategoryStreamsVariables
) {
    companion object {
        const val CATEGORY_STREAMS_QUERY = """
            query CategoryStreams_Query(${'$'}name: String!, ${'$'}first: Int!) {
              game(name: ${'$'}name) {
                id
                name
                displayName
                boxArtURL(width: 285, height: 380)
                viewersCount
                streams(first: ${'$'}first) {
                  edges {
                    node {
                      id
                      title
                      viewersCount
                      previewImageURL(width: 440, height: 248)
                      broadcaster {
                        id
                        login
                        displayName
                      }
                      game {
                        name
                        displayName
                      }
                    }
                  }
                }
              }
            }
        """
    }
}

data class GqlCategoryStreamsVariables(
    @SerializedName("name") val name: String,
    @SerializedName("first") val first: Int = 40
)

data class GqlCategoryStreamsResponse(
    @SerializedName("data") val data: GqlCategoryStreamsData?
)

data class GqlCategoryStreamsData(
    @SerializedName("game") val game: GqlCategoryWithStreamsNode?
)

data class GqlCategoryWithStreamsNode(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("boxArtURL") val boxArtUrl: String?,
    @SerializedName("viewersCount") val viewersCount: Int?,
    @SerializedName("streams") val streams: GqlStreamConnection?
)

data class GqlStreamConnection(
    @SerializedName("edges") val edges: List<GqlStreamEdge>?
)

data class GqlStreamEdge(
    @SerializedName("node") val node: GqlStreamNode?
)

data class GqlStreamNode(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("viewersCount") val viewersCount: Int?,
    @SerializedName("previewImageURL") val previewImageUrl: String?,
    @SerializedName("broadcaster") val broadcaster: GqlBroadcasterNode?,
    @SerializedName("game") val game: GqlSimpleGameNode?
)

data class GqlBroadcasterNode(
    @SerializedName("id") val id: String?,
    @SerializedName("login") val login: String?,
    @SerializedName("displayName") val displayName: String?
)

data class GqlSimpleGameNode(
    @SerializedName("name") val name: String?,
    @SerializedName("displayName") val displayName: String?
)
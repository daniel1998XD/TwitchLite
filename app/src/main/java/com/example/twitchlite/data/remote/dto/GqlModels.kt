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

// No topo do seu GqlModels.kt, atualize a classe GqlData para incluir videoToken:
data class GqlData(
    @SerializedName("streamPlaybackAccessToken") val token: StreamPlaybackAccessToken?,
    @SerializedName("videoPlaybackAccessToken") val videoToken: StreamPlaybackAccessToken? = null
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

// ==================== MODELOS GQL PARA BUSCA GLOBAL (AUTOCOMPLETE E TELA DE BUSCA) ====================

data class GqlSearchUsersRequest(
    @SerializedName("operationName") val operationName: String = "SearchUsers_Query",
    @SerializedName("query") val query: String = SEARCH_USERS_QUERY,
    @SerializedName("variables") val variables: GqlSearchUsersVariables
) {
    companion object {
        const val SEARCH_USERS_QUERY = """
            query SearchUsers_Query(${'$'}userQuery: String!, ${'$'}first: Int!) {
              searchUsers(userQuery: ${'$'}userQuery, first: ${'$'}first) {
                edges {
                  node {
                    id
                    login
                    displayName
                    profileImageURL(width: 70)
                    roles {
                      isPartner
                    }
                    broadcastSettings {
                      title
                      game {
                        id
                        name
                        displayName
                      }
                    }
                    stream {
                      id
                      title
                      viewersCount
                      previewImageURL(width: 440, height: 248)
                      game {
                        id
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

data class GqlSearchUsersVariables(
    @SerializedName("userQuery") val userQuery: String,
    @SerializedName("first") val first: Int = 15
)

data class GqlSearchUsersResponse(
    @SerializedName("data") val data: GqlSearchUsersData?
)

data class GqlSearchUsersData(
    @SerializedName("searchUsers") val searchUsers: GqlSearchUsersConnection?
)

data class GqlSearchUsersConnection(
    @SerializedName("edges") val edges: List<GqlSearchUserEdge>?
)

data class GqlSearchUserEdge(
    @SerializedName("node") val node: GqlSearchUserNode?
)

data class GqlSearchUserNode(
    @SerializedName("id") val id: String?,
    @SerializedName("login") val login: String?,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("profileImageURL") val profileImageUrl: String?,
    @SerializedName("roles") val roles: GqlUserRoles?,
    @SerializedName("broadcastSettings") val broadcastSettings: GqlBroadcastSettings?,
    @SerializedName("stream") val stream: GqlSearchUserStream?
)

data class GqlUserRoles(
    @SerializedName("isPartner") val isPartner: Boolean?
)

data class GqlBroadcastSettings(
    @SerializedName("title") val title: String?,
    @SerializedName("game") val game: GqlSimpleGameNode?
)

data class GqlSearchUserStream(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("viewersCount") val viewersCount: Int?,
    @SerializedName("previewImageURL") val previewImageUrl: String?,
    @SerializedName("game") val game: GqlSimpleGameNode?
)

data class GqlSearchCategoriesRequest(
    @SerializedName("operationName") val operationName: String = "SearchCategories_Query",
    @SerializedName("query") val query: String = SEARCH_CATEGORIES_QUERY,
    @SerializedName("variables") val variables: GqlSearchCategoriesVariables
) {
    companion object {
        const val SEARCH_CATEGORIES_QUERY = """
            query SearchCategories_Query(${'$'}query: String!, ${'$'}first: Int!) {
              searchCategories(query: ${'$'}query, first: ${'$'}first) {
                edges {
                  node {
                    id
                    name
                    displayName
                    boxArtURL(width: 285, height: 380)
                    viewersCount
                  }
                }
              }
            }
        """
    }
}

data class GqlSearchCategoriesVariables(
    @SerializedName("query") val query: String,
    @SerializedName("first") val first: Int = 10
)

data class GqlSearchCategoriesResponse(
    @SerializedName("data") val data: GqlSearchCategoriesData?
)

data class GqlSearchCategoriesData(
    @SerializedName("searchCategories") val searchCategories: GqlSearchCategoriesConnection?
)

data class GqlSearchCategoriesConnection(
    @SerializedName("edges") val edges: List<GqlSearchCategoryEdge>?
)

data class GqlSearchCategoryEdge(
    @SerializedName("node") val node: GqlGameNode?
)

// ==================== MODELOS GQL PARA VODS E CLIPS (PLAYBACK + FALLBACK) ====================

data class GqlClipPlaybackRequest(
    @SerializedName("operationName") val operationName: String = "ClipPlayback_Query",
    @SerializedName("query") val query: String = CLIP_PLAYBACK_QUERY,
    @SerializedName("variables") val variables: Map<String, String>
) {
    companion object {
        const val CLIP_PLAYBACK_QUERY = """
            query ClipPlayback_Query(${'$'}slug: ID!) {
              clip(slug: ${'$'}slug) {
                id
                slug
                videoQualities {
                  frameRate
                  quality
                  sourceURL
                }
                playbackAccessToken(params: {platform: "web", playerBackend: "mediaplayer", playerType: "embed"}) {
                  signature
                  value
                }
              }
            }
        """
    }
}

data class GqlClipPlaybackResponse(
    @SerializedName("data") val data: GqlClipPlaybackData?
)

data class GqlClipPlaybackData(
    @SerializedName("clip") val clip: GqlClipPlaybackNode?
)

data class GqlClipPlaybackNode(
    @SerializedName("id") val id: String?,
    @SerializedName("slug") val slug: String?,
    @SerializedName("videoQualities") val videoQualities: List<GqlClipQualityNode>?,
    @SerializedName("playbackAccessToken") val playbackAccessToken: StreamPlaybackAccessToken?
)

data class GqlClipQualityNode(
    @SerializedName("frameRate") val frameRate: Float?,
    @SerializedName("quality") val quality: String?,
    @SerializedName("sourceURL") val sourceUrl: String?
)

data class GqlChannelVideosAndClipsRequest(
    @SerializedName("operationName") val operationName: String = "ChannelVideosAndClips_Query",
    @SerializedName("query") val query: String = CHANNEL_VIDEOS_CLIPS_QUERY,
    @SerializedName("variables") val variables: Map<String, Any>
) {
    companion object {
        const val CHANNEL_VIDEOS_CLIPS_QUERY = """
            query ChannelVideosAndClips_Query(${'$'}login: String!, ${'$'}first: Int!) {
              user(login: ${'$'}login) {
                id
                login
                displayName
                videos(first: ${'$'}first) {
                  edges {
                    node {
                      id
                      title
                      viewCount
                      lengthSeconds
                      publishedAt
                      previewThumbnailURL(width: 440, height: 248)
                    }
                  }
                }
                clips(first: ${'$'}first, criteria: { period: ALL_TIME }) {
                  edges {
                    node {
                      id
                      slug
                      title
                      viewCount
                      durationSeconds
                      createdAt
                      thumbnailURL(width: 440, height: 248)
                      curator {
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

data class GqlChannelVideosAndClipsResponse(
    @SerializedName("data") val data: GqlChannelVideosAndClipsData?
)

data class GqlChannelVideosAndClipsData(
    @SerializedName("user") val user: GqlChannelVideosClipsUserNode?
)

data class GqlChannelVideosClipsUserNode(
    @SerializedName("id") val id: String?,
    @SerializedName("login") val login: String?,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("videos") val videos: GqlVideoConnection?,
    @SerializedName("clips") val clips: GqlClipConnection?
)

data class GqlVideoConnection(
    @SerializedName("edges") val edges: List<GqlVideoEdge>?
)

data class GqlVideoEdge(
    @SerializedName("node") val node: GqlVideoNode?
)

data class GqlVideoNode(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("viewCount") val viewCount: Int?,
    @SerializedName("lengthSeconds") val lengthSeconds: Int?,
    @SerializedName("publishedAt") val publishedAt: String?,
    @SerializedName("previewThumbnailURL") val previewThumbnailUrl: String?
)

data class GqlClipConnection(
    @SerializedName("edges") val edges: List<GqlClipEdge>?
)

data class GqlClipEdge(
    @SerializedName("node") val node: GqlClipNode?
)

data class GqlClipNode(
    @SerializedName("id") val id: String?,
    @SerializedName("slug") val slug: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("viewCount") val viewCount: Int?,
    @SerializedName("durationSeconds") val durationSeconds: Int?,
    @SerializedName("createdAt") val createdAt: String?,
    @SerializedName("thumbnailURL") val thumbnailUrl: String?,
    @SerializedName("curator") val curator: GqlBroadcasterNode?
)
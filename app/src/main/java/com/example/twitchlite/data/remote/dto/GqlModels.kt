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
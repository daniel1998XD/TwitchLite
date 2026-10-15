package com.example.twitchlite.data.remote

import android.net.Uri
import com.example.twitchlite.data.local.TokenManager
import com.example.twitchlite.data.remote.api.TwitchGqlApi
import com.example.twitchlite.data.remote.dto.GqlPlaybackTokenRequest
import com.example.twitchlite.data.remote.dto.GqlVariables
import kotlin.random.Random

class StreamExtractorRepository(
    private val gqlApi: TwitchGqlApi,
    private val tokenManager: TokenManager
) {
    /**
     * Ative para `true` caso queira rotear a busca do Master Playlist (.m3u8) por um proxy
     * compatível com TwitchAdSolutions (ex: Luminous) no futuro.
     */
    private val useThirdPartyAdBlockProxy: Boolean = false

    suspend fun extractHlsMasterPlaylistUrl(channelLogin: String): Result<String> {
        return runCatching {
            val cleanLogin = channelLogin.trim().lowercase()

            // =========================================================================
            // PONTO DE INJEÇÃO (PROXY EXTERNO - PADRÃO TWITCHADSOLUTIONS)
            // =========================================================================
            // Caso a Twitch passe a bloquear o parâmetro playerType="embed" no GQL nativo,
            // basta alterar `useThirdPartyAdBlockProxy = true` para delegar a requisição
            // do manifesto .m3u8 a um proxy reverso isento de anúncios.
            if (useThirdPartyAdBlockProxy) {
                return@runCatching buildProxyHlsUrl(cleanLogin)
            }

            // =========================================================================
            // EXTRAÇÃO NATIVA ANTI-ANÚNCIOS (ABORDAGEM STREAMLINK)
            // =========================================================================
            val userToken = tokenManager.getAccessToken()
            val authHeader = null

            val requestBody = GqlPlaybackTokenRequest(
                variables = GqlVariables(
                    isLive = true,
                    login = cleanLogin,
                    playerType = "embed",          // Evita injeção de pre-rolls/mid-rolls
                    platform = "web",
                    playerBackend = "mediaplayer"
                )
            )

            val response = gqlApi.getPlaybackAccessToken(
                oauthHeader = authHeader,
                request = requestBody
            )

            val accessToken = response.data?.token
                ?: throw IllegalStateException("Canal '$cleanLogin' está offline ou indisponível.")

            buildUsherHlsUrl(
                channel = cleanLogin,
                signature = accessToken.signature,
                tokenValue = accessToken.value
            )
        }
    }

    private fun buildUsherHlsUrl(channel: String, signature: String, tokenValue: String): String {
        return Uri.Builder()
            .scheme("https")
            .authority("usher.ttvnw.net")
            .appendPath("api")
            .appendPath("channel")
            .appendPath("hls")
            .appendPath("$channel.m3u8")
            .appendQueryParameter("player", "twitchweb")
            .appendQueryParameter("allow_source", "true")
            .appendQueryParameter("allow_audio_only", "true")
            .appendQueryParameter("allow_spectre", "false")
            .appendQueryParameter("fast_bread", "true")
            .appendQueryParameter("p", Random.nextInt(100_000, 999_999).toString())
            .appendQueryParameter("sig", signature)
            .appendQueryParameter("token", tokenValue)
            .build()
            .toString()
    }

    private fun buildProxyHlsUrl(channel: String): String {
        return Uri.Builder()
            .scheme("https")
            .authority("eu.luminous.dev")
            .appendPath("live")
            .appendPath(channel)
            .appendQueryParameter("allow_source", "true")
            .appendQueryParameter("allow_audio_only", "true")
            .appendQueryParameter("fast_bread", "true")
            .build()
            .toString()
    }
}
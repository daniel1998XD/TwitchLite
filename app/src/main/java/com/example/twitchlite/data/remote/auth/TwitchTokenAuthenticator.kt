package com.example.twitchlite.data.remote.auth

import com.example.twitchlite.data.local.TokenManager
import com.example.twitchlite.data.remote.api.TwitchAuthRefreshApi
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TwitchTokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authRefreshApi: TwitchAuthRefreshApi,
    private val clientId: String,
    private val clientSecret: String
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Evita loop infinito caso a renovação falhe mais de uma vez
        if (responseCount(response) >= 2) return null

        synchronized(this) {
            val currentToken = tokenManager.getAccessToken()
            val requestToken = response.request.header("Authorization")
                ?.removePrefix("Bearer ")
                ?.removePrefix("OAuth ")
                ?.trim()

            // Se outra thread já renovou o token, apenas refaz a chamada com o token novo
            if (!currentToken.isNullOrBlank() && currentToken != requestToken) {
                return rebuildRequestWithNewToken(response.request, currentToken)
            }

            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken.isNullOrBlank() || clientSecret.isBlank()) {
                return null
            }

            val refreshResponse = runCatching {
                authRefreshApi.refreshTokenSync(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    refreshToken = refreshToken
                ).execute()
            }.getOrNull()

            val newTokens = refreshResponse?.body()
            if (refreshResponse?.isSuccessful == true && newTokens != null) {
                tokenManager.updateTokens(
                    accessToken = newTokens.accessToken,
                    refreshToken = newTokens.refreshToken
                )
                return rebuildRequestWithNewToken(response.request, newTokens.accessToken)
            }

            return null
        }
    }

    private fun rebuildRequestWithNewToken(request: Request, newToken: String): Request {
        val oldHeader = request.header("Authorization") ?: ""
        val prefix = if (oldHeader.startsWith("OAuth", ignoreCase = true)) "OAuth" else "Bearer"

        return request.newBuilder()
            .header("Authorization", "$prefix $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
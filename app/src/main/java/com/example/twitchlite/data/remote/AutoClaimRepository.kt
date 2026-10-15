package com.example.twitchlite.data.remote

import com.example.twitchlite.data.local.TokenManager
import com.example.twitchlite.data.remote.api.TwitchGqlApi
import com.example.twitchlite.data.remote.dto.GqlExtensions
import com.example.twitchlite.data.remote.dto.GqlGenericRequest
import com.example.twitchlite.data.remote.dto.GqlPersistedQuery

class AutoClaimRepository(
    private val gqlApi: TwitchGqlApi,
    private val tokenManager: TokenManager
) {
    companion object {
        private const val WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
        // Hashes PersistedQuery oficiais do GQL da Twitch
        private const val SHA256_CHANNEL_POINTS_CONTEXT = "9988086babc615a918a1e9a722ff41d98847acac822645209ac7379eecb27152"
        private const val SHA256_CLAIM_COMMUNITY_POINTS = "46aaeebe02c99afdf4fc97c7c0cba964124bf6b0af229395f1f6d1feed05b3d0"
        private const val SHA256_INVENTORY_DROPS = "e8b98b52bbd7ccd37d0b671ad0d47be5238caa5bea637d2a65776175b4a23a64"
        private const val SHA256_CLAIM_DROP_REWARDS = "a455deea71bdc9015b78eb49f4acfbce8baa7ccbedd28e549bb025bd0f751930"
    }

    /**
     * Verifica se há baú de Pontos do Canal (Channel Points) ou Twitch Drops prontos
     * para resgate e executa a mutação silenciosamente.
     * Retorna uma mensagem caso algo tenha sido resgatado.
     */
    suspend fun checkAndClaimAll(channelLogin: String): String? {
        val token = tokenManager.getAccessToken() ?: return null
        val oauthHeader = "OAuth $token"
        val clientId = tokenManager.getClientId()?.ifBlank { WEB_CLIENT_ID } ?: WEB_CLIENT_ID

        val claimedNotifications = mutableListOf<String>()

        // 1. Auto-Claim de Bônus de Pontos do Canal (Baú +50 pts)
        runCatching {
            val contextReq = GqlGenericRequest(
                operationName = "ChannelPointsContext",
                variables = mapOf("channelLogin" to channelLogin.lowercase()),
                extensions = GqlExtensions(GqlPersistedQuery(sha256Hash = SHA256_CHANNEL_POINTS_CONTEXT))
            )
            val contextRes = gqlApi.queryChannelPointsContext(oauthHeader, clientId, contextReq)
            val channelId = contextRes.data?.community?.channelId
            val claimId = contextRes.data?.community?.channel?.self?.communityPoints?.availableClaim?.claimId

            if (!channelId.isNullOrBlank() && !claimId.isNullOrBlank()) {
                val claimReq = GqlGenericRequest(
                    operationName = "ClaimCommunityPoints",
                    variables = mapOf(
                        "input" to mapOf(
                            "channelID" to channelId,
                            "claimID" to claimId
                        )
                    ),
                    extensions = GqlExtensions(GqlPersistedQuery(sha256Hash = SHA256_CLAIM_COMMUNITY_POINTS))
                )
                gqlApi.executeAutoClaimMutation(oauthHeader, clientId, claimReq)
                claimedNotifications.add("Baú de Pontos do Canal resgatado!")
            }
        }

        // 2. Auto-Claim de Twitch Drops (Campanhas ativas com 100% de tempo assistido)
        runCatching {
            val dropsReq = GqlGenericRequest(
                operationName = "Inventory",
                variables = emptyMap(),
                extensions = GqlExtensions(GqlPersistedQuery(sha256Hash = SHA256_INVENTORY_DROPS))
            )
            val dropsRes = gqlApi.queryDropsInventory(oauthHeader, clientId, dropsReq)
            val campaigns = dropsRes.data?.currentUser?.inventory?.campaigns.orEmpty()

            for (campaign in campaigns) {
                for (drop in campaign.timeBasedDrops.orEmpty()) {
                    val self = drop.self ?: continue
                    val instanceId = self.dropInstanceId
                    val isReadyToClaim = !self.isClaimed &&
                            !instanceId.isNullOrBlank() &&
                            self.currentMinutesWatched >= self.requiredMinutesWatched

                    if (isReadyToClaim) {
                        val claimDropReq = GqlGenericRequest(
                            operationName = "DropsPage_ClaimDropRewards",
                            variables = mapOf(
                                "input" to mapOf("dropInstanceID" to instanceId)
                            ),
                            extensions = GqlExtensions(GqlPersistedQuery(sha256Hash = SHA256_CLAIM_DROP_REWARDS))
                        )
                        gqlApi.executeAutoClaimMutation(oauthHeader, clientId, claimDropReq)
                        claimedNotifications.add("Drop resgatado: ${drop.name ?: "Recompensa"}")
                    }
                }
            }
        }

        return if (claimedNotifications.isEmpty()) null else claimedNotifications.joinToString(" • ")
    }
}
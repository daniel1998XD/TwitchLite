package com.example.twitchlite.data.remote.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.twitchlite.data.local.TokenManager
import com.example.twitchlite.data.remote.api.TwitchGqlApi
import com.example.twitchlite.data.remote.api.TwitchHelixApi
import com.example.twitchlite.data.remote.dto.GqlFollowedLiveStreamsRequest
import com.example.twitchlite.data.remote.dto.GqlFollowedLiveStreamsVariables
import com.example.twitchlite.data.remote.dto.HelixStreamDto
import com.google.gson.Gson
import com.google.gson.JsonObject

class FollowedStreamsPagingSource(
    private val helixApi: TwitchHelixApi,
    private val gqlApi: TwitchGqlApi,
    private val tokenManager: TokenManager,
    private val clientId: String
) : PagingSource<String, HelixStreamDto>() {

    private val gson = Gson()

    override fun getRefreshKey(state: PagingState<String, HelixStreamDto>): String? {
        return null
    }

    override suspend fun load(params: LoadParams<String>): LoadResult<String, HelixStreamDto> {
        val rawToken = tokenManager.getAccessToken()?.trim()
        if (rawToken.isNullOrBlank()) {
            return LoadResult.Error(
                IllegalStateException("Faça login na sua conta da Twitch para listar seus canais seguidos.")
            )
        }

        val cleanToken = rawToken
            .removePrefix("Bearer ")
            .removePrefix("OAuth ")
            .removePrefix("oauth:")
            .trim()

        return try {
            // 1ª Opção (Principal): Busca Canais Seguidos ao Vivo via GQL (compatível com o Login WebView)
            val gqlResult = runCatching {
                val response = gqlApi.queryFollowedLiveStreams(
                    oauthHeader = "OAuth $cleanToken",
                    request = GqlFollowedLiveStreamsRequest(
                        variables = GqlFollowedLiveStreamsVariables(
                            first = params.loadSize.coerceIn(10, 30),
                            after = params.key
                        )
                    )
                )

                val currentUser = response.data?.currentUser
                    ?: throw IllegalStateException("Sessão GQL expirada ou inválida.")

                val connection = currentUser.followedLiveUsers
                val edges = connection?.edges.orEmpty()

                val streams = edges.mapNotNull { edge ->
                    val userNode = edge.node ?: return@mapNotNull null
                    val streamNode = userNode.stream ?: return@mapNotNull null
                    val login = userNode.login?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

                    val json = JsonObject().apply {
                        addProperty("id", streamNode.id ?: userNode.id ?: login)
                        addProperty("user_id", userNode.id ?: "")
                        addProperty("user_login", login)
                        addProperty(
                            "user_name",
                            userNode.displayName?.takeIf { it.isNotBlank() } ?: login
                        )
                        addProperty("game_id", "")
                        addProperty(
                            "game_name",
                            streamNode.game?.displayName
                                ?: streamNode.game?.name
                                ?: "Ao Vivo"
                        )
                        addProperty("type", streamNode.type ?: "live")
                        addProperty("title", streamNode.title.orEmpty())
                        addProperty("viewer_count", streamNode.viewersCount ?: 0)
                        addProperty("started_at", streamNode.createdAt.orEmpty())
                        addProperty("language", "")
                        addProperty(
                            "thumbnail_url",
                            streamNode.previewImageUrl?.takeIf { it.isNotBlank() }
                                ?: "https://static-cdn.jtvnw.net/previews-ttv/live_user_${login}-{width}x{height}.jpg"
                        )
                    }

                    gson.fromJson(json, HelixStreamDto::class.java)
                }

                val hasNextPage = connection?.pageInfo?.hasNextPage == true
                val nextCursor = if (hasNextPage) edges.lastOrNull()?.cursor else null

                LoadResult.Page(
                    data = streams,
                    prevKey = null,
                    nextKey = nextCursor
                )
            }

            if (gqlResult.isSuccess) {
                return gqlResult.getOrThrow()
            }

            // 2ª Opção (Fallback): API Helix caso esteja usando token de desenvolvedor
            val userId = tokenManager.getUserId()
                ?: return LoadResult.Error(IllegalStateException("User ID não encontrado."))

            val response = helixApi.getFollowedStreams(
                bearerToken = "Bearer $cleanToken",
                clientId = clientId,
                userId = userId,
                pageSize = params.loadSize.coerceAtMost(100),
                afterCursor = params.key
            )

            LoadResult.Page(
                data = response.streams,
                prevKey = null,
                nextKey = response.pagination?.cursor
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
package com.example.twitchlite.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.twitchlite.data.local.TokenManager
import com.example.twitchlite.data.remote.api.TwitchGqlApi
import com.example.twitchlite.data.remote.api.TwitchHelixApi
import com.example.twitchlite.data.remote.dto.GqlFollowedGamesRequest
import com.example.twitchlite.data.remote.dto.GqlFollowedGamesVariables
import com.example.twitchlite.data.remote.dto.GqlGameLookupRequest
import com.example.twitchlite.data.remote.dto.GqlGameNode
import com.example.twitchlite.data.remote.dto.HelixStreamDto
import com.example.twitchlite.data.remote.paging.FollowedStreamsPagingSource
import com.example.twitchlite.di.AppContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FollowedCategoryUiModel(
    val id: String,
    val name: String,
    val displayName: String,
    val boxArtUrl: String,
    val viewersCount: Int = 0
)

data class HomeUiState(
    val followedCategories: List<FollowedCategoryUiModel> = emptyList(),
    val isLoadingCategories: Boolean = false
)

class HomeViewModel(
    private val helixApi: TwitchHelixApi,
    private val gqlApi: TwitchGqlApi,
    private val tokenManager: TokenManager
) : ViewModel() {

    private var currentPagingSource: FollowedStreamsPagingSource? = null

    private val _authStatusMessage = MutableStateFlow<String?>(null)
    val authStatusMessage: StateFlow<String?> = _authStatusMessage.asStateFlow()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val followedStreamsPagingFlow: Flow<PagingData<HelixStreamDto>> = Pager(
        config = PagingConfig(
            pageSize = 20,
            initialLoadSize = 20,
            prefetchDistance = 5,
            enablePlaceholders = false
        ),
        pagingSourceFactory = {
            FollowedStreamsPagingSource(
                helixApi = helixApi,
                tokenManager = tokenManager,
                clientId = AppContainer.TWITCH_CLIENT_ID
            ).also { currentPagingSource = it }
        }
    ).flow.cachedIn(viewModelScope)

    init {
        loadFollowedCategories()
    }

    /**
     * Salva o Token OAuth e consulta `/helix/users` para descobrir automaticamente
     * o `user_id` e `login` do usuário logado, recarregando a lista Paging 3 e as categorias.
     */
    fun authenticateWithToken(accessToken: String, customClientId: String) {
        val cleanToken = accessToken
            .removePrefix("Bearer ")
            .removePrefix("OAuth ")
            .removePrefix("oauth:")
            .trim()

        if (customClientId.isNotBlank()) {
            AppContainer.TWITCH_CLIENT_ID = customClientId.trim()
            tokenManager.saveClientId(customClientId.trim())
        }

        viewModelScope.launch {
            runCatching {
                val userResponse = helixApi.getCurrentUser(
                    bearerToken = "Bearer $cleanToken",
                    clientId = AppContainer.TWITCH_CLIENT_ID
                )
                val user = userResponse.users.firstOrNull()
                    ?: throw IllegalStateException("Nenhum usuário retornado pela Helix API.")

                tokenManager.saveAuthData(
                    accessToken = cleanToken,
                    refreshToken = null,
                    userId = user.id,
                    username = user.login
                )
            }.onSuccess {
                _authStatusMessage.value = "Autenticado como @${tokenManager.getUsername()}"
                refreshStreams()
            }.onFailure { err ->
                _authStatusMessage.value = "Erro na autenticação Helix: ${err.localizedMessage}"
            }
        }
    }

    fun refreshStreams() {
        currentPagingSource?.invalidate()
        loadFollowedCategories()
    }

    fun loadFollowedCategories() {
        val username = tokenManager.getUsername().orEmpty().trim()
        val token = tokenManager.getAccessToken()?.trim()

        if (username.isBlank() && token.isNullOrBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCategories = true) }

            val request = GqlFollowedGamesRequest(
                variables = GqlFollowedGamesVariables(
                    login = username,
                    first = 30
                )
            )

            // 1ª Tentativa: Com OAuth (caso o token seja compatível com o GQL Client-ID)
            val authNodes = if (!token.isNullOrBlank()) {
                runCatching {
                    val res = gqlApi.queryFollowedGames(
                        oauthHeader = "OAuth $token",
                        request = request
                    )
                    res.data?.currentUser?.followedGames?.nodes
                        ?: res.data?.user?.followedGames?.nodes
                }.getOrNull()
            } else null

            // 2ª Tentativa: Sem header de Auth (evita erro 401 quando o token é exclusivo da Helix)
            val finalNodes = if (!authNodes.isNullOrEmpty()) {
                authNodes
            } else if (username.isNotBlank()) {
                runCatching {
                    val res = gqlApi.queryFollowedGames(
                        oauthHeader = null,
                        request = request
                    )
                    res.data?.user?.followedGames?.nodes
                        ?: res.data?.currentUser?.followedGames?.nodes
                }.getOrNull()
            } else null

            val mapped = finalNodes
                .orEmpty()
                .map { it.toUiModel() }
                .distinctBy { it.id }

            _uiState.update { state ->
                state.copy(
                    followedCategories = if (mapped.isNotEmpty()) mapped else state.followedCategories,
                    isLoadingCategories = false
                )
            }
        }
    }

    /**
     * Fallback via GQL (oauthHeader = null): caso a Twitch restrinja `followedGames` para tokens
     * de Client-ID terceiros da Helix, busca no GQL os dados e BoxArt das categorias presentes
     * nos canais seguidos pelo usuário.
     */
    fun syncCategoriesFromFollowedStreamsFallback(streams: List<HelixStreamDto>) {
        if (_uiState.value.followedCategories.isNotEmpty() || _uiState.value.isLoadingCategories) return

        val distinctGameNames = streams
            .map { it.gameName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(15)

        if (distinctGameNames.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCategories = true) }

            val fetchedCategories = distinctGameNames.map { gameName ->
                async {
                    runCatching {
                        val response = gqlApi.queryGameByName(
                            oauthHeader = null,
                            request = GqlGameLookupRequest(variables = mapOf("name" to gameName))
                        )
                        response.data?.game?.toUiModel()
                    }.getOrNull() ?: FollowedCategoryUiModel(
                        id = gameName,
                        name = gameName,
                        displayName = gameName,
                        boxArtUrl = "",
                        viewersCount = streams.filter { it.gameName == gameName }.sumOf { it.viewerCount }
                    )
                }
            }.awaitAll()

            _uiState.update {
                it.copy(
                    followedCategories = fetchedCategories.distinctBy { cat -> cat.id },
                    isLoadingCategories = false
                )
            }
        }
    }

    private fun GqlGameNode.toUiModel(): FollowedCategoryUiModel {
        return FollowedCategoryUiModel(
            id = id,
            name = name,
            displayName = displayName?.takeIf { it.isNotBlank() } ?: name,
            boxArtUrl = getFormattedBoxArtUrl(width = 285, height = 380),
            viewersCount = viewersCount ?: 0
        )
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(
                        helixApi = container.helixApi,
                        gqlApi = container.gqlApi,
                        tokenManager = container.tokenManager
                    ) as T
                }
            }
    }
}
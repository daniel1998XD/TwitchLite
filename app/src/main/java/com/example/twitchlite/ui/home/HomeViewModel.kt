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
import com.example.twitchlite.data.remote.dto.GqlCategoryStreamsRequest
import com.example.twitchlite.data.remote.dto.GqlCategoryStreamsVariables
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

data class CategoryStreamUiModel(
    val id: String,
    val userLogin: String,
    val userName: String,
    val gameName: String,
    val title: String,
    val viewerCount: Int,
    val thumbnailUrl: String
)

data class HomeUiState(
    val followedCategories: List<FollowedCategoryUiModel> = emptyList(),
    val isLoadingCategories: Boolean = false,
    val selectedCategory: FollowedCategoryUiModel? = null,
    val categoryStreams: List<CategoryStreamUiModel> = emptyList(),
    val isLoadingCategoryStreams: Boolean = false,
    val categoryStreamsError: String? = null
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

    /**
     * Abre a janela da categoria clicada e busca as lives ao vivo daquele jogo via GQL.
     */
    fun openCategoryStreams(category: FollowedCategoryUiModel) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                categoryStreams = emptyList(),
                isLoadingCategoryStreams = true,
                categoryStreamsError = null
            )
        }

        viewModelScope.launch {
            runCatching {
                val response = gqlApi.queryCategoryStreams(
                    oauthHeader = null,
                    request = GqlCategoryStreamsRequest(
                        variables = GqlCategoryStreamsVariables(
                            name = category.name,
                            first = 40
                        )
                    )
                )

                val gameNode = response.data?.game
                val updatedCategory = if (gameNode != null) {
                    category.copy(
                        id = gameNode.id ?: category.id,
                        displayName = gameNode.displayName?.takeIf { it.isNotBlank() } ?: category.displayName,
                        boxArtUrl = gameNode.boxArtUrl?.takeIf { it.isNotBlank() } ?: category.boxArtUrl,
                        viewersCount = gameNode.viewersCount ?: category.viewersCount
                    )
                } else {
                    category
                }

                val streams = gameNode?.streams?.edges
                    .orEmpty()
                    .mapNotNull { edge ->
                        val node = edge.node ?: return@mapNotNull null
                        val login = node.broadcaster?.login?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        CategoryStreamUiModel(
                            id = node.id ?: login,
                            userLogin = login,
                            userName = node.broadcaster.displayName?.takeIf { it.isNotBlank() } ?: login,
                            gameName = node.game?.displayName ?: updatedCategory.displayName,
                            title = node.title.orEmpty(),
                            viewerCount = node.viewersCount ?: 0,
                            thumbnailUrl = node.previewImageUrl.orEmpty()
                        )
                    }

                Pair(updatedCategory, streams)
            }.onSuccess { (updatedCategory, streams) ->
                _uiState.update {
                    it.copy(
                        selectedCategory = updatedCategory,
                        categoryStreams = streams,
                        isLoadingCategoryStreams = false,
                        categoryStreamsError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingCategoryStreams = false,
                        categoryStreamsError = err.localizedMessage ?: "Erro ao carregar transmissões da categoria."
                    )
                }
            }
        }
    }

    fun closeCategoryStreams() {
        _uiState.update {
            it.copy(
                selectedCategory = null,
                categoryStreams = emptyList(),
                isLoadingCategoryStreams = false,
                categoryStreamsError = null
            )
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
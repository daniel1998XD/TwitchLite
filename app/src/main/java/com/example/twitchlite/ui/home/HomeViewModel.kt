package com.example.twitchlite.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.twitchlite.data.local.TokenManager
import com.example.twitchlite.data.remote.api.TwitchHelixApi
import com.example.twitchlite.data.remote.dto.HelixStreamDto
import com.example.twitchlite.data.remote.paging.FollowedStreamsPagingSource
import com.example.twitchlite.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val helixApi: TwitchHelixApi,
    private val tokenManager: TokenManager
) : ViewModel() {

    private var currentPagingSource: FollowedStreamsPagingSource? = null

    private val _authStatusMessage = MutableStateFlow<String?>(null)
    val authStatusMessage: StateFlow<String?> = _authStatusMessage.asStateFlow()

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

    /**
     * Salva o Token OAuth e consulta `/helix/users` para descobrir automaticamente
     * o `user_id` e `login` do usuário logado, recarregando a lista Paging 3 em seguida.
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
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(
                        helixApi = container.helixApi,
                        tokenManager = container.tokenManager
                    ) as T
                }
            }
    }
}
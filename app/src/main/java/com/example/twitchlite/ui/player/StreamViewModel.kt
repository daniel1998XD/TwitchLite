package com.example.twitchlite.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.twitchlite.data.local.TokenManager
import com.example.twitchlite.data.remote.StreamExtractorRepository
import com.example.twitchlite.data.remote.irc.TwitchIrcClient
import com.example.twitchlite.di.AppContainer
import com.example.twitchlite.domain.model.BufferConfig
import com.example.twitchlite.domain.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StreamUiState {
    data object Loading : StreamUiState
    data class Ready(
        val channelLogin: String,
        val hlsUrl: String,
        val bufferConfig: BufferConfig
    ) : StreamUiState
    data class Error(val message: String) : StreamUiState
}

class StreamViewModel(
    private val extractorRepository: StreamExtractorRepository,
    private val ircClient: TwitchIrcClient,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<StreamUiState>(StreamUiState.Loading)
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessage>> = ircClient.messages

    fun loadStream(channelLogin: String, customBufferConfig: BufferConfig = BufferConfig()) {
        val cleanChannel = channelLogin.trim().lowercase()
        val currentState = _uiState.value
        if (currentState is StreamUiState.Ready && currentState.channelLogin == cleanChannel) {
            return
        }

        _uiState.value = StreamUiState.Loading

        viewModelScope.launch {
            extractorRepository.extractHlsMasterPlaylistUrl(cleanChannel)
                .onSuccess { m3u8Url ->
                    _uiState.value = StreamUiState.Ready(
                        channelLogin = cleanChannel,
                        hlsUrl = m3u8Url,
                        bufferConfig = customBufferConfig
                    )
                }
                .onFailure { error ->
                    _uiState.value = StreamUiState.Error(
                        error.message ?: "Falha ao extrair fluxo HLS (.m3u8)"
                    )
                }
        }

        // Conecta ao WebSocket IRC (autenticado se houver token, ou leitura anônima via justinfan)
        ircClient.connect(
            channel = cleanChannel,
            oauthToken = tokenManager.getAccessToken(),
            username = tokenManager.getUsername()
        )
    }

    fun sendChatMessage(message: String) {
        ircClient.sendMessage(message)
    }

    fun stopStream() {
        ircClient.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        ircClient.disconnect()
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StreamViewModel(
                        extractorRepository = container.streamExtractorRepository,
                        ircClient = container.ircClient,
                        tokenManager = container.tokenManager
                    ) as T
                }
            }
    }
}
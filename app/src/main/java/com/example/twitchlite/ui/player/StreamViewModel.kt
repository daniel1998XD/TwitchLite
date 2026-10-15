package com.example.twitchlite.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.twitchlite.data.local.TokenManager
import com.example.twitchlite.data.remote.AutoClaimRepository
import com.example.twitchlite.data.remote.EmoteRepository
import com.example.twitchlite.data.remote.StreamExtractorRepository
import com.example.twitchlite.data.remote.irc.TwitchIrcClient
import com.example.twitchlite.di.AppContainer
import com.example.twitchlite.domain.model.BufferConfig
import com.example.twitchlite.domain.model.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
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
    private val emoteRepository: EmoteRepository,
    private val autoClaimRepository: AutoClaimRepository,
    private val ircClient: TwitchIrcClient,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<StreamUiState>(StreamUiState.Loading)
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessage>> = ircClient.messages
    val thirdPartyEmotes: StateFlow<Map<String, String>> = emoteRepository.activeEmotes

    private val _lastAutoClaimNotification = MutableStateFlow<String?>(null)
    val lastAutoClaimNotification: StateFlow<String?> = _lastAutoClaimNotification.asStateFlow()

    private var autoClaimJob: Job? = null

    init {
        // Quando o IRC detectar o room-id do canal atual, carrega os emotes 7TV/BTTV/FFZ dele
        viewModelScope.launch {
            ircClient.currentRoomId.collectLatest { roomId ->
                if (!roomId.isNullOrBlank()) {
                    emoteRepository.loadChannelEmotes(roomId)
                }
            }
        }
    }

    fun loadStream(channelLogin: String, customBufferConfig: BufferConfig = BufferConfig()) {
        val cleanChannel = channelLogin.trim().lowercase()
        val currentState = _uiState.value
        if (currentState is StreamUiState.Ready && currentState.channelLogin == cleanChannel) {
            return
        }

        _uiState.value = StreamUiState.Loading

        // 1. Pré-carrega emotes globais 7TV/BTTV/FFZ
        viewModelScope.launch {
            emoteRepository.loadGlobalEmotesIfNeeded()
        }

        // 2. Extrai a Master Playlist HLS (.m3u8)
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

        // 3. Conecta ao Chat IRC
        ircClient.connect(
            channel = cleanChannel,
            oauthToken = tokenManager.getAccessToken(),
            username = tokenManager.getUsername()
        )

        // 4. Inicia o loop de Auto-Claim de Channel Points e Twitch Drops em background
        startAutoClaimLoop(cleanChannel)
    }

    private fun startAutoClaimLoop(channelLogin: String) {
        autoClaimJob?.cancel()
        autoClaimJob = viewModelScope.launch {
            // Aguarda 15s iniciais após abrir a live e depois verifica a cada 60s
            delay(15_000L)
            while (isActive) {
                val claimResult = autoClaimRepository.checkAndClaimAll(channelLogin)
                if (!claimResult.isNullOrBlank()) {
                    _lastAutoClaimNotification.value = claimResult
                }
                delay(60_000L)
            }
        }
    }

    fun consumeAutoClaimNotification() {
        _lastAutoClaimNotification.value = null
    }

    fun sendChatMessage(message: String) {
        ircClient.sendMessage(message)
    }

    fun stopStream() {
        autoClaimJob?.cancel()
        ircClient.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        autoClaimJob?.cancel()
        ircClient.disconnect()
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StreamViewModel(
                        extractorRepository = container.streamExtractorRepository,
                        emoteRepository = container.emoteRepository,
                        autoClaimRepository = container.autoClaimRepository,
                        ircClient = container.ircClient,
                        tokenManager = container.tokenManager
                    ) as T
                }
            }
    }
}
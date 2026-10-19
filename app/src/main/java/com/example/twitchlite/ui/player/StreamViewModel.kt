package com.example.twitchlite.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.twitchlite.data.local.TokenManager
import com.example.twitchlite.data.remote.AutoClaimRepository
import com.example.twitchlite.data.remote.EmoteRepository
import com.example.twitchlite.data.remote.StreamExtractorRepository
import com.example.twitchlite.data.remote.api.TwitchGqlApi
import com.example.twitchlite.data.remote.api.TwitchHelixApi
import com.example.twitchlite.data.remote.dto.GqlChannelVideosAndClipsRequest
import com.example.twitchlite.data.remote.dto.GqlClipPlaybackRequest
import com.example.twitchlite.data.remote.dto.GqlPlaybackTokenRequest
import com.example.twitchlite.data.remote.dto.GqlVariables
import com.example.twitchlite.data.remote.irc.TwitchIrcClient
import com.example.twitchlite.di.AppContainer
import com.example.twitchlite.domain.model.BufferConfig
import com.example.twitchlite.domain.model.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLEncoder

enum class PlaybackContentType {
    LIVE,
    VOD,
    CLIP,
    OFFLINE
}

data class ChannelVodUiModel(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val durationFormatted: String,
    val viewCount: Int,
    val createdAtFormatted: String
)

data class ChannelClipUiModel(
    val id: String,
    val slug: String,
    val title: String,
    val thumbnailUrl: String,
    val durationFormatted: String,
    val viewCount: Int,
    val creatorName: String,
    val createdAtFormatted: String
)

sealed interface StreamUiState {
    data object Loading : StreamUiState
    data class Ready(
        val channelLogin: String,
        val hlsUrl: String?, // Null quando o canal está Offline e nenhum VOD/Clip foi selecionado ainda
        val bufferConfig: BufferConfig,
        val contentType: PlaybackContentType = PlaybackContentType.LIVE,
        val isChannelCurrentlyLive: Boolean = true,
        val liveHlsUrlBackup: String? = null,
        val activeMediaTitle: String? = null
    ) : StreamUiState
    data class Error(val message: String) : StreamUiState
}

class StreamViewModel(
    private val extractorRepository: StreamExtractorRepository,
    private val emoteRepository: EmoteRepository,
    private val autoClaimRepository: AutoClaimRepository,
    private val ircClient: TwitchIrcClient,
    private val tokenManager: TokenManager,
    private val helixApi: TwitchHelixApi,
    private val gqlApi: TwitchGqlApi
) : ViewModel() {

    private val _uiState = MutableStateFlow<StreamUiState>(StreamUiState.Loading)
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessage>> = ircClient.messages
    val thirdPartyEmotes: StateFlow<Map<String, String>> = emoteRepository.activeEmotes

    private val _lastAutoClaimNotification = MutableStateFlow<String?>(null)
    val lastAutoClaimNotification: StateFlow<String?> = _lastAutoClaimNotification.asStateFlow()

    // Estados das Abas de VODs e Clips do Canal
    private val _channelVods = MutableStateFlow<List<ChannelVodUiModel>>(emptyList())
    val channelVods: StateFlow<List<ChannelVodUiModel>> = _channelVods.asStateFlow()

    private val _channelClips = MutableStateFlow<List<ChannelClipUiModel>>(emptyList())
    val channelClips: StateFlow<List<ChannelClipUiModel>> = _channelClips.asStateFlow()

    private val _isLoadingMediaLists = MutableStateFlow(false)
    val isLoadingMediaLists: StateFlow<Boolean> = _isLoadingMediaLists.asStateFlow()

    private val _isLoadingVodPlayback = MutableStateFlow(false)
    val isLoadingVodPlayback: StateFlow<Boolean> = _isLoadingVodPlayback.asStateFlow()

    private var autoClaimJob: Job? = null
    private var currentLoadedChannel: String? = null

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
        if (currentLoadedChannel == cleanChannel && _uiState.value is StreamUiState.Ready) {
            return
        }
        currentLoadedChannel = cleanChannel

        _uiState.value = StreamUiState.Loading
        _channelVods.value = emptyList()
        _channelClips.value = emptyList()

        // 1. Pré-carrega emotes globais 7TV/BTTV/FFZ
        viewModelScope.launch {
            emoteRepository.loadGlobalEmotesIfNeeded()
        }

        // 2. Busca VODs (/helix/videos) e Clips (/helix/clips) do canal em paralelo
        loadChannelVodsAndClips(cleanChannel)

        // 3. Tenta extrair a Master Playlist HLS (.m3u8) da Live
        // Se o canal estiver OFFLINE, entra em modo Perfil Offline (sem travar em tela de erro!)
        viewModelScope.launch {
            extractorRepository.extractHlsMasterPlaylistUrl(cleanChannel)
                .onSuccess { m3u8Url ->
                    _uiState.value = StreamUiState.Ready(
                        channelLogin = cleanChannel,
                        hlsUrl = m3u8Url,
                        bufferConfig = customBufferConfig,
                        contentType = PlaybackContentType.LIVE,
                        isChannelCurrentlyLive = true,
                        liveHlsUrlBackup = m3u8Url,
                        activeMediaTitle = "Transmissão ao Vivo • @$cleanChannel"
                    )
                }
                .onFailure {
                    // Canal está Offline: abre a tela do canal com os VODs e Clips disponíveis
                    _uiState.value = StreamUiState.Ready(
                        channelLogin = cleanChannel,
                        hlsUrl = null,
                        bufferConfig = customBufferConfig,
                        contentType = PlaybackContentType.OFFLINE,
                        isChannelCurrentlyLive = false,
                        liveHlsUrlBackup = null,
                        activeMediaTitle = null
                    )
                }
        }

        // 4. Conecta ao Chat IRC (funciona inclusive em chat de canais offline)
        ircClient.connect(
            channel = cleanChannel,
            oauthToken = tokenManager.getAccessToken(),
            username = tokenManager.getUsername()
        )

        // 5. Inicia o loop de Auto-Claim de Channel Points e Twitch Drops em background
        startAutoClaimLoop(cleanChannel)
    }

    /**
     * Busca a lista de VODs e Clips do canal usando a API Helix (/helix/videos e /helix/clips),
     * com fallback automático via GQL caso o token OAuth não esteja configurado.
     */
    fun loadChannelVodsAndClips(channelLogin: String = currentLoadedChannel.orEmpty()) {
        val cleanChannel = channelLogin.trim().lowercase()
        if (cleanChannel.isBlank()) return

        viewModelScope.launch {
            _isLoadingMediaLists.value = true

            val accessToken = tokenManager.getAccessToken()?.trim()
            val clientId = AppContainer.TWITCH_CLIENT_ID

            var loadedFromHelix = false

            if (!accessToken.isNullOrBlank() && clientId.isNotBlank()) {
                runCatching {
                    val bearer = "Bearer $accessToken"
                    val userRes = helixApi.getUserByLogin(
                        bearerToken = bearer,
                        clientId = clientId,
                        login = cleanChannel
                    )
                    val targetUserId = userRes.users.firstOrNull()?.id
                        ?: throw IllegalStateException("Usuário não encontrado na Helix")

                    coroutineScope {
                        val vodsDeferred = async {
                            helixApi.getChannelVideos(
                                bearerToken = bearer,
                                clientId = clientId,
                                userId = targetUserId,
                                first = 30,
                                type = "all"
                            ).videos.map { dto ->
                                ChannelVodUiModel(
                                    id = dto.id,
                                    title = dto.title,
                                    thumbnailUrl = dto.getFormattedThumbnail(440, 248),
                                    durationFormatted = formatHelixDuration(dto.duration),
                                    viewCount = dto.viewCount,
                                    createdAtFormatted = formatIsoDate(dto.createdAt)
                                )
                            }
                        }

                        val clipsDeferred = async {
                            helixApi.getChannelClips(
                                bearerToken = bearer,
                                clientId = clientId,
                                broadcasterId = targetUserId,
                                first = 30
                            ).clips.map { dto ->
                                ChannelClipUiModel(
                                    id = dto.id,
                                    slug = dto.id,
                                    title = dto.title,
                                    thumbnailUrl = dto.thumbnailUrl.orEmpty(),
                                    durationFormatted = "${dto.duration.toInt()}s",
                                    viewCount = dto.viewCount,
                                    creatorName = dto.creatorName ?: cleanChannel,
                                    createdAtFormatted = formatIsoDate(dto.createdAt)
                                )
                            }
                        }

                        Pair(vodsDeferred.await(), clipsDeferred.await())
                    }
                }.onSuccess { (vods, clips) ->
                    _channelVods.value = vods
                    _channelClips.value = clips
                    loadedFromHelix = true
                }
            }

            // Fallback via GQL (oauthHeader = null) caso o usuário não esteja logado na Helix
            if (!loadedFromHelix) {
                runCatching {
                    val response = gqlApi.queryChannelVideosAndClips(
                        oauthHeader = null,
                        request = GqlChannelVideosAndClipsRequest(
                            variables = mapOf(
                                "login" to cleanChannel,
                                "first" to 30
                            )
                        )
                    )
                    val userNode = response.data?.user
                    val vods = userNode?.videos?.edges.orEmpty().mapNotNull { edge ->
                        val node = edge.node ?: return@mapNotNull null
                        val id = node.id ?: return@mapNotNull null
                        ChannelVodUiModel(
                            id = id,
                            title = node.title ?: "VOD #$id",
                            thumbnailUrl = node.previewThumbnailUrl.orEmpty(),
                            durationFormatted = formatSecondsToDuration(node.lengthSeconds ?: 0),
                            viewCount = node.viewCount ?: 0,
                            createdAtFormatted = formatIsoDate(node.publishedAt)
                        )
                    }
                    val clips = userNode?.clips?.edges.orEmpty().mapNotNull { edge ->
                        val node = edge.node ?: return@mapNotNull null
                        val slug = node.slug ?: node.id ?: return@mapNotNull null
                        ChannelClipUiModel(
                            id = node.id ?: slug,
                            slug = slug,
                            title = node.title ?: "Clip",
                            thumbnailUrl = node.thumbnailUrl.orEmpty(),
                            durationFormatted = "${node.durationSeconds ?: 0}s",
                            viewCount = node.viewCount ?: 0,
                            creatorName = node.curator?.displayName ?: cleanChannel,
                            createdAtFormatted = formatIsoDate(node.createdAt)
                        )
                    }
                    Pair(vods, clips)
                }.onSuccess { (vods, clips) ->
                    _channelVods.value = vods
                    _channelClips.value = clips
                }
            }

            _isLoadingMediaLists.value = false
        }
    }

    /**
     * Extrai a URL HLS (.m3u8) de um VOD sem anúncios via GQL + Usher e inicia a reprodução no ExoPlayer.
     */
    fun playVod(vod: ChannelVodUiModel) {
        val currentState = _uiState.value as? StreamUiState.Ready ?: return
        _isLoadingVodPlayback.value = true

        viewModelScope.launch {
            runCatching {
                val tokenResponse = gqlApi.getPlaybackAccessToken(
                    oauthHeader = null,
                    request = GqlPlaybackTokenRequest(
                        variables = GqlVariables(
                            isLive = false,
                            login = "",
                            isVod = true,
                            vodId = vod.id,
                            playerType = "embed",
                            platform = "web",
                            playerBackend = "mediaplayer"
                        )
                    )
                )

                val vodToken = tokenResponse.data?.videoToken
                    ?: throw IllegalStateException("Token de acesso do VOD não retornado pelo GQL.")

                val encodedValue = URLEncoder.encode(vodToken.value, "UTF-8")
                val randomP = (100000..999999).random()

                "https://usher.ttvnw.net/vod/${vod.id}.m3u8" +
                        "?nauth=$encodedValue" +
                        "&nauthsig=${vodToken.signature}" +
                        "&allow_source=true" +
                        "&allow_audio_only=true" +
                        "&player_backend=mediaplayer" +
                        "&playlist_include_framerate=true" +
                        "&p=$randomP"
            }.onSuccess { vodM3u8Url ->
                _uiState.value = currentState.copy(
                    hlsUrl = vodM3u8Url,
                    contentType = PlaybackContentType.VOD,
                    activeMediaTitle = "VOD: ${vod.title}"
                )
                _isLoadingVodPlayback.value = false
            }.onFailure { err ->
                _lastAutoClaimNotification.value =
                    "Não foi possível abrir o VOD: ${err.localizedMessage ?: "Restrito para inscritos"}"
                _isLoadingVodPlayback.value = false
            }
        }
    }

    /**
     * Extrai a URL direta (.mp4) de um Clip via GQL e reproduz no ExoPlayer com barra de progresso.
     */
    fun playClip(clip: ChannelClipUiModel) {
        val currentState = _uiState.value as? StreamUiState.Ready ?: return
        _isLoadingVodPlayback.value = true

        viewModelScope.launch {
            runCatching {
                val response = gqlApi.queryClipPlayback(
                    oauthHeader = null,
                    request = GqlClipPlaybackRequest(
                        variables = mapOf("slug" to clip.slug)
                    )
                )
                val clipNode = response.data?.clip
                    ?: throw IllegalStateException("Dados do Clip não encontrados.")
                val bestQuality = clipNode.videoQualities?.firstOrNull()
                    ?: throw IllegalStateException("Nenhuma qualidade disponível para este Clip.")
                val token = clipNode.playbackAccessToken
                    ?: throw IllegalStateException("Token do Clip ausente.")

                val encodedToken = URLEncoder.encode(token.value, "UTF-8")
                "${bestQuality.sourceUrl}?sig=${token.signature}&token=$encodedToken"
            }.onSuccess { clipMp4Url ->
                _uiState.value = currentState.copy(
                    hlsUrl = clipMp4Url,
                    contentType = PlaybackContentType.CLIP,
                    activeMediaTitle = "Clip: ${clip.title}"
                )
                _isLoadingVodPlayback.value = false
            }.onFailure { err ->
                _lastAutoClaimNotification.value =
                    "Erro ao reproduzir Clip: ${err.localizedMessage}"
                _isLoadingVodPlayback.value = false
            }
        }
    }

    /**
     * Retorna para a transmissão ao vivo caso o usuário estivesse assistindo a um VOD/Clip
     * de um canal que está em live no momento.
     */
    fun returnToLiveStream() {
        val currentState = _uiState.value as? StreamUiState.Ready ?: return
        val backupLiveUrl = currentState.liveHlsUrlBackup ?: return
        _uiState.value = currentState.copy(
            hlsUrl = backupLiveUrl,
            contentType = PlaybackContentType.LIVE,
            activeMediaTitle = "Transmissão ao Vivo • @${currentState.channelLogin}"
        )
    }

    private fun startAutoClaimLoop(channelLogin: String) {
        autoClaimJob?.cancel()
        autoClaimJob = viewModelScope.launch {
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
        currentLoadedChannel = null
    }

    override fun onCleared() {
        super.onCleared()
        autoClaimJob?.cancel()
        ircClient.disconnect()
    }

    private fun formatHelixDuration(raw: String): String {
        if (raw.isBlank()) return ""
        // Converte "4h12m30s" ou "25m10s" para formato legível "04:12:30"
        val hours = Regex("(\\d+)h").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = Regex("(\\d+)m").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val seconds = Regex("(\\d+)s").find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun formatSecondsToDuration(totalSeconds: Int): String {
        if (totalSeconds <= 0) return "00:00"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun formatIsoDate(isoDate: String?): String {
        if (isoDate.isNullOrBlank() || isoDate.length < 10) return ""
        // Converte "2026-10-18T..." para "18/10/2026"
        val parts = isoDate.substring(0, 10).split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else isoDate.take(10)
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
                        tokenManager = container.tokenManager,
                        helixApi = container.helixApi,
                        gqlApi = container.gqlApi
                    ) as T
                }
            }
    }
}
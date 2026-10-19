package com.example.twitchlite.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.twitchlite.data.local.TokenManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class PlaybackContentType {
    LIVE,
    VOD,
    CLIP,
    OFFLINE
}

/**
 * Pacotes de filtro de tempo para VODs (ordenados por data) e Clips (ordenados por visualizações).
 */
enum class MediaTimeFilter(
    val label: String,
    val helixVodPeriod: String,
    val gqlClipPeriod: String
) {
    HOURS_24("24 horas", "day", "LAST_DAY"),
    WEEK("1 semana", "week", "LAST_WEEK"),
    MONTH("1 mês", "month", "LAST_MONTH"),
    YEAR("Neste ano", "all", "ALL_TIME"),
    ALL("Todos", "all", "ALL_TIME")
}

data class ChannelVodUiModel(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val durationFormatted: String,
    val viewCount: Int,
    val createdAtIso: String,
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
    val createdAtIso: String,
    val createdAtFormatted: String
)

sealed interface StreamUiState {
    data object Loading : StreamUiState
    data class Ready(
        val channelLogin: String,
        val hlsUrl: String?,
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
    private val ircClient: TwitchIrcClient,
    private val tokenManager: TokenManager,
    private val helixApi: TwitchHelixApi,
    private val gqlApi: TwitchGqlApi
) : ViewModel() {

    private val _uiState = MutableStateFlow<StreamUiState>(StreamUiState.Loading)
    val uiState: StateFlow<StreamUiState> = _uiState.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessage>> = ircClient.messages
    val thirdPartyEmotes: StateFlow<Map<String, String>> = emoteRepository.activeEmotes

    // ==================== ESTADOS DE VODS (PAGINAÇÃO + FILTRO DE DATA) ====================
    private val _selectedVodFilter = MutableStateFlow(MediaTimeFilter.ALL)
    val selectedVodFilter: StateFlow<MediaTimeFilter> = _selectedVodFilter.asStateFlow()

    private val _channelVods = MutableStateFlow<List<ChannelVodUiModel>>(emptyList())
    val channelVods: StateFlow<List<ChannelVodUiModel>> = _channelVods.asStateFlow()

    private val _isLoadingVods = MutableStateFlow(false)
    val isLoadingVods: StateFlow<Boolean> = _isLoadingVods.asStateFlow()

    private val _isLoadingMoreVods = MutableStateFlow(false)
    val isLoadingMoreVods: StateFlow<Boolean> = _isLoadingMoreVods.asStateFlow()

    private val _canLoadMoreVods = MutableStateFlow(false)
    val canLoadMoreVods: StateFlow<Boolean> = _canLoadMoreVods.asStateFlow()

    // ==================== ESTADOS DE CLIPS (PAGINAÇÃO + FILTRO DE VIEWS) ====================
    private val _selectedClipFilter = MutableStateFlow(MediaTimeFilter.ALL)
    val selectedClipFilter: StateFlow<MediaTimeFilter> = _selectedClipFilter.asStateFlow()

    private val _channelClips = MutableStateFlow<List<ChannelClipUiModel>>(emptyList())
    val channelClips: StateFlow<List<ChannelClipUiModel>> = _channelClips.asStateFlow()

    private val _isLoadingClips = MutableStateFlow(false)
    val isLoadingClips: StateFlow<Boolean> = _isLoadingClips.asStateFlow()

    private val _isLoadingMoreClips = MutableStateFlow(false)
    val isLoadingMoreClips: StateFlow<Boolean> = _isLoadingMoreClips.asStateFlow()

    private val _canLoadMoreClips = MutableStateFlow(false)
    val canLoadMoreClips: StateFlow<Boolean> = _canLoadMoreClips.asStateFlow()

    private val _isLoadingVodPlayback = MutableStateFlow(false)
    val isLoadingVodPlayback: StateFlow<Boolean> = _isLoadingVodPlayback.asStateFlow()

    private var currentLoadedChannel: String? = null
    private var cachedHelixUserId: String? = null

    private var vodsNextCursor: String? = null
    private var clipsNextCursor: String? = null

    init {
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
        cachedHelixUserId = null
        vodsNextCursor = null
        clipsNextCursor = null

        _uiState.value = StreamUiState.Loading
        _channelVods.value = emptyList()
        _channelClips.value = emptyList()
        _selectedVodFilter.value = MediaTimeFilter.ALL
        _selectedClipFilter.value = MediaTimeFilter.ALL

        // 1. Pré-carrega emotes globais 7TV/BTTV/FFZ
        viewModelScope.launch {
            emoteRepository.loadGlobalEmotesIfNeeded()
        }

        // 2. Carrega a primeira página de VODs e Clips do canal
        fetchVods(reset = true)
        fetchClips(reset = true)

        // 3. Tenta extrair a Master Playlist HLS (.m3u8) da Live
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

        // 4. Conecta ao Chat IRC
        ircClient.connect(
            channel = cleanChannel,
            oauthToken = tokenManager.getAccessToken(),
            username = tokenManager.getUsername()
        )
    }

    fun onVodFilterSelected(filter: MediaTimeFilter) {
        if (_selectedVodFilter.value == filter && _channelVods.value.isNotEmpty()) return
        _selectedVodFilter.value = filter
        fetchVods(reset = true)
    }

    fun loadMoreVods() {
        if (_isLoadingVods.value || _isLoadingMoreVods.value || !_canLoadMoreVods.value) return
        fetchVods(reset = false)
    }

    fun onClipFilterSelected(filter: MediaTimeFilter) {
        if (_selectedClipFilter.value == filter && _channelClips.value.isNotEmpty()) return
        _selectedClipFilter.value = filter
        fetchClips(reset = true)
    }

    fun loadMoreClips() {
        if (_isLoadingClips.value || _isLoadingMoreClips.value || !_canLoadMoreClips.value) return
        fetchClips(reset = false)
    }

    private suspend fun resolveHelixUserId(channelLogin: String): String? {
        cachedHelixUserId?.let { return it }
        val accessToken = tokenManager.getAccessToken()?.trim()
        val clientId = AppContainer.TWITCH_CLIENT_ID
        if (accessToken.isNullOrBlank() || clientId.isBlank()) return null

        return runCatching {
            val res = helixApi.getUserByLogin(
                bearerToken = "Bearer $accessToken",
                clientId = clientId,
                login = channelLogin
            )
            res.users.firstOrNull()?.id?.also { cachedHelixUserId = it }
        }.getOrNull()
    }

    /**
     * Busca VODs ordenados pela data mais recente (sort = "time"),
     * respeitando o filtro de período selecionado e o cursor de paginação ("Ver mais").
     */
    private fun fetchVods(reset: Boolean) {
        val cleanChannel = currentLoadedChannel ?: return
        val activeFilter = _selectedVodFilter.value

        if (reset) {
            vodsNextCursor = null
            _channelVods.value = emptyList()
            _canLoadMoreVods.value = false
            _isLoadingVods.value = true
        } else {
            _isLoadingMoreVods.value = true
        }

        viewModelScope.launch {
            val accessToken = tokenManager.getAccessToken()?.trim()
            val clientId = AppContainer.TWITCH_CLIENT_ID
            val cutoffMillis = getCutoffEpochMillis(activeFilter)

            var handledByHelix = false

            if (!accessToken.isNullOrBlank() && clientId.isNotBlank()) {
                val userId = resolveHelixUserId(cleanChannel)
                if (!userId.isNullOrBlank()) {
                    runCatching {
                        val response = helixApi.getChannelVideos(
                            bearerToken = "Bearer $accessToken",
                            clientId = clientId,
                            userId = userId,
                            first = 20,
                            afterCursor = vodsNextCursor,
                            sort = "time",
                            period = activeFilter.helixVodPeriod,
                            type = "all"
                        )

                        val rawList = response.videos.map { dto ->
                            ChannelVodUiModel(
                                id = dto.id,
                                title = dto.title,
                                thumbnailUrl = dto.getFormattedThumbnail(440, 248),
                                durationFormatted = formatHelixDuration(dto.duration),
                                viewCount = dto.viewCount,
                                createdAtIso = dto.createdAt.orEmpty(),
                                createdAtFormatted = formatIsoDate(dto.createdAt)
                            )
                        }

                        val filteredList = if (cutoffMillis != null) {
                            rawList.filter { isIsoDateAfterOrEqual(it.createdAtIso, cutoffMillis) }
                        } else {
                            rawList
                        }

                        val nextCursor = response.pagination?.cursor
                        val reachedTimeLimit = cutoffMillis != null && filteredList.size < rawList.size

                        Triple(filteredList, nextCursor, !nextCursor.isNullOrBlank() && !reachedTimeLimit && rawList.isNotEmpty())
                    }.onSuccess { (newVods, nextCursor, hasMore) ->
                        vodsNextCursor = nextCursor
                        _channelVods.value = (_channelVods.value + newVods)
                            .distinctBy { it.id }
                            .sortedByDescending { it.createdAtIso }
                        _canLoadMoreVods.value = hasMore
                        handledByHelix = true
                    }
                }
            }

            if (!handledByHelix) {
                runCatching {
                    val response = gqlApi.queryChannelVideosAndClips(
                        oauthHeader = null,
                        request = GqlChannelVideosAndClipsRequest(
                            variables = mapOf(
                                "login" to cleanChannel,
                                "first" to 20,
                                "vodAfter" to vodsNextCursor,
                                "clipAfter" to null,
                                "clipPeriod" to activeFilter.gqlClipPeriod
                            )
                        )
                    )
                    val videoConn = response.data?.user?.videos
                    val edges = videoConn?.edges.orEmpty()

                    val rawList = edges.mapNotNull { edge ->
                        val node = edge.node ?: return@mapNotNull null
                        val id = node.id ?: return@mapNotNull null
                        ChannelVodUiModel(
                            id = id,
                            title = node.title ?: "VOD #$id",
                            thumbnailUrl = node.previewThumbnailUrl.orEmpty(),
                            durationFormatted = formatSecondsToDuration(node.lengthSeconds ?: 0),
                            viewCount = node.viewCount ?: 0,
                            createdAtIso = node.publishedAt.orEmpty(),
                            createdAtFormatted = formatIsoDate(node.publishedAt)
                        )
                    }

                    val filteredList = if (cutoffMillis != null) {
                        rawList.filter { isIsoDateAfterOrEqual(it.createdAtIso, cutoffMillis) }
                    } else {
                        rawList
                    }

                    val lastCursor = edges.lastOrNull()?.cursor
                    val hasNextPage = videoConn?.pageInfo?.hasNextPage == true
                    val reachedTimeLimit = cutoffMillis != null && filteredList.size < rawList.size

                    Triple(filteredList, lastCursor, hasNextPage && !reachedTimeLimit && !lastCursor.isNullOrBlank())
                }.onSuccess { (newVods, nextCursor, hasMore) ->
                    vodsNextCursor = nextCursor
                    _channelVods.value = (_channelVods.value + newVods)
                        .distinctBy { it.id }
                        .sortedByDescending { it.createdAtIso }
                    _canLoadMoreVods.value = hasMore
                }
            }

            _isLoadingVods.value = false
            _isLoadingMoreVods.value = false
        }
    }

    /**
     * Busca Clips ordenados por mais visualizações (viewCount decrescente),
     * aplicando o intervalo RFC3339 (started_at / ended_at) na Helix e o cursor de paginação ("Ver mais").
     */
    private fun fetchClips(reset: Boolean) {
        val cleanChannel = currentLoadedChannel ?: return
        val activeFilter = _selectedClipFilter.value

        if (reset) {
            clipsNextCursor = null
            _channelClips.value = emptyList()
            _canLoadMoreClips.value = false
            _isLoadingClips.value = true
        } else {
            _isLoadingMoreClips.value = true
        }

        viewModelScope.launch {
            val accessToken = tokenManager.getAccessToken()?.trim()
            val clientId = AppContainer.TWITCH_CLIENT_ID
            val cutoffMillis = getCutoffEpochMillis(activeFilter)
            val (startedAtRfc3339, endedAtRfc3339) = getHelixClipDateRange(activeFilter)

            var handledByHelix = false

            if (!accessToken.isNullOrBlank() && clientId.isNotBlank()) {
                val userId = resolveHelixUserId(cleanChannel)
                if (!userId.isNullOrBlank()) {
                    runCatching {
                        val response = helixApi.getChannelClips(
                            bearerToken = "Bearer $accessToken",
                            clientId = clientId,
                            broadcasterId = userId,
                            first = 20,
                            afterCursor = clipsNextCursor,
                            startedAt = startedAtRfc3339,
                            endedAt = endedAtRfc3339
                        )

                        val mappedClips = response.clips.map { dto ->
                            ChannelClipUiModel(
                                id = dto.id,
                                slug = dto.id,
                                title = dto.title,
                                thumbnailUrl = dto.thumbnailUrl.orEmpty(),
                                durationFormatted = "${dto.duration.toInt()}s",
                                viewCount = dto.viewCount,
                                creatorName = dto.creatorName ?: cleanChannel,
                                createdAtIso = dto.createdAt.orEmpty(),
                                createdAtFormatted = formatIsoDate(dto.createdAt)
                            )
                        }

                        val nextCursor = response.pagination?.cursor
                        Triple(mappedClips, nextCursor, !nextCursor.isNullOrBlank() && mappedClips.isNotEmpty())
                    }.onSuccess { (newClips, nextCursor, hasMore) ->
                        clipsNextCursor = nextCursor
                        _channelClips.value = (_channelClips.value + newClips)
                            .distinctBy { it.id }
                            .sortedByDescending { it.viewCount }
                        _canLoadMoreClips.value = hasMore
                        handledByHelix = true
                    }
                }
            }

            if (!handledByHelix) {
                runCatching {
                    val response = gqlApi.queryChannelVideosAndClips(
                        oauthHeader = null,
                        request = GqlChannelVideosAndClipsRequest(
                            variables = mapOf(
                                "login" to cleanChannel,
                                "first" to 20,
                                "vodAfter" to null,
                                "clipAfter" to clipsNextCursor,
                                "clipPeriod" to activeFilter.gqlClipPeriod
                            )
                        )
                    )
                    val clipConn = response.data?.user?.clips
                    val edges = clipConn?.edges.orEmpty()

                    val rawClips = edges.mapNotNull { edge ->
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
                            createdAtIso = node.createdAt.orEmpty(),
                            createdAtFormatted = formatIsoDate(node.createdAt)
                        )
                    }

                    val filteredClips = if (activeFilter == MediaTimeFilter.YEAR && cutoffMillis != null) {
                        rawClips.filter { isIsoDateAfterOrEqual(it.createdAtIso, cutoffMillis) }
                    } else {
                        rawClips
                    }

                    val lastCursor = edges.lastOrNull()?.cursor
                    val hasNextPage = clipConn?.pageInfo?.hasNextPage == true

                    Triple(filteredClips, lastCursor, hasNextPage && !lastCursor.isNullOrBlank())
                }.onSuccess { (newClips, nextCursor, hasMore) ->
                    clipsNextCursor = nextCursor
                    _channelClips.value = (_channelClips.value + newClips)
                        .distinctBy { it.id }
                        .sortedByDescending { it.viewCount }
                    _canLoadMoreClips.value = hasMore
                }
            }

            _isLoadingClips.value = false
            _isLoadingMoreClips.value = false
        }
    }

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
            }.onFailure {
                _isLoadingVodPlayback.value = false
            }
        }
    }

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
            }.onFailure {
                _isLoadingVodPlayback.value = false
            }
        }
    }

    fun returnToLiveStream() {
        val currentState = _uiState.value as? StreamUiState.Ready ?: return
        val backupLiveUrl = currentState.liveHlsUrlBackup ?: return
        _uiState.value = currentState.copy(
            hlsUrl = backupLiveUrl,
            contentType = PlaybackContentType.LIVE,
            activeMediaTitle = "Transmissão ao Vivo • @${currentState.channelLogin}"
        )
    }

    fun sendChatMessage(message: String) {
        ircClient.sendMessage(message)
    }

    fun stopStream() {
        ircClient.disconnect()
        currentLoadedChannel = null
    }

    override fun onCleared() {
        super.onCleared()
        ircClient.disconnect()
    }

    // ==================== HELPERS DE JANELA DE TEMPO (RFC3339 UTC) ====================

    private fun getCutoffEpochMillis(filter: MediaTimeFilter): Long? {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return when (filter) {
            MediaTimeFilter.HOURS_24 -> {
                cal.add(Calendar.HOUR_OF_DAY, -24)
                cal.timeInMillis
            }
            MediaTimeFilter.WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.timeInMillis
            }
            MediaTimeFilter.MONTH -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                cal.timeInMillis
            }
            MediaTimeFilter.YEAR -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            MediaTimeFilter.ALL -> null
        }
    }

    private fun getHelixClipDateRange(filter: MediaTimeFilter): Pair<String?, String?> {
        val cutoffMillis = getCutoffEpochMillis(filter) ?: return Pair(null, null)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val startedAt = sdf.format(cutoffMillis)
        val endedAt = sdf.format(System.currentTimeMillis())
        return Pair(startedAt, endedAt)
    }

    private fun isIsoDateAfterOrEqual(isoDate: String?, cutoffMillis: Long): Boolean {
        if (isoDate.isNullOrBlank()) return true
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = sdf.parse(isoDate)?.time ?: return true
            parsed >= cutoffMillis
        }.getOrDefault(true)
    }

    private fun formatHelixDuration(raw: String): String {
        if (raw.isBlank()) return ""
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
                        ircClient = container.ircClient,
                        tokenManager = container.tokenManager,
                        helixApi = container.helixApi,
                        gqlApi = container.gqlApi
                    ) as T
                }
            }
    }
}
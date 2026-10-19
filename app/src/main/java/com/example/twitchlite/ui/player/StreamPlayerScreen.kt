package com.example.twitchlite.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.twitchlite.MainActivity
import com.example.twitchlite.domain.model.BufferConfig
import com.example.twitchlite.domain.model.ChatMessage
import com.example.twitchlite.domain.model.VideoQualityOption
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class ChannelContentTab(val title: String) {
    CHAT("Chat Ao Vivo"),
    VODS("VODs"),
    CLIPS("Clips")
}

@Composable
fun rememberIsInPipMode(): Boolean {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() as? ComponentActivity }
    var isInPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }

    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPip = info.isInPictureInPictureMode
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(listener)
        }
    }
    return isInPip
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamPlayerScreen(
    channelLogin: String,
    viewModel: StreamViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isInPipMode = rememberIsInPipMode()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val thirdPartyEmotes by viewModel.thirdPartyEmotes.collectAsStateWithLifecycle()
    val autoClaimNotice by viewModel.lastAutoClaimNotification.collectAsStateWithLifecycle()

    // Estados de VODs (Filtro de tempo + Lista + Paginação)
    val selectedVodFilter by viewModel.selectedVodFilter.collectAsStateWithLifecycle()
    val channelVods by viewModel.channelVods.collectAsStateWithLifecycle()
    val isLoadingVods by viewModel.isLoadingVods.collectAsStateWithLifecycle()
    val isLoadingMoreVods by viewModel.isLoadingMoreVods.collectAsStateWithLifecycle()
    val canLoadMoreVods by viewModel.canLoadMoreVods.collectAsStateWithLifecycle()

    // Estados de Clips (Filtro de tempo + Lista + Paginação)
    val selectedClipFilter by viewModel.selectedClipFilter.collectAsStateWithLifecycle()
    val channelClips by viewModel.channelClips.collectAsStateWithLifecycle()
    val isLoadingClips by viewModel.isLoadingClips.collectAsStateWithLifecycle()
    val isLoadingMoreClips by viewModel.isLoadingMoreClips.collectAsStateWithLifecycle()
    val canLoadMoreClips by viewModel.canLoadMoreClips.collectAsStateWithLifecycle()

    val isLoadingVodPlayback by viewModel.isLoadingVodPlayback.collectAsStateWithLifecycle()

    var availableQualities by remember { mutableStateOf<List<VideoQualityOption>>(emptyList()) }
    var selectedQuality by remember { mutableStateOf<VideoQualityOption?>(null) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    var selectedTab by rememberSaveable(channelLogin) { mutableStateOf(ChannelContentTab.CHAT) }
    var hasAutoSwitchedForOffline by rememberSaveable(channelLogin) { mutableStateOf(false) }

    LaunchedEffect(channelLogin) {
        viewModel.loadStream(channelLogin)
    }

    LaunchedEffect(uiState) {
        val readyState = uiState as? StreamUiState.Ready
        if (readyState != null && !readyState.isChannelCurrentlyLive && !hasAutoSwitchedForOffline) {
            selectedTab = ChannelContentTab.VODS
            hasAutoSwitchedForOffline = true
        }
    }

    LaunchedEffect(autoClaimNotice) {
        val notice = autoClaimNotice
        if (!notice.isNullOrBlank()) {
            Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
            viewModel.consumeAutoClaimNotification()
        }
    }

    val toggleFullscreen: () -> Unit = {
        val activity = context.findActivity()
        if (activity != null) {
            val currentlyLandscape =
                activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (currentlyLandscape) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    BackHandler {
        val activity = context.findActivity()
        val currentlyLandscape =
            activity?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (currentlyLandscape && activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            viewModel.stopStream()
            onBack()
        }
    }

    LaunchedEffect(uiState) {
        val mainActivity = context.findActivity() as? MainActivity
        val ready = uiState as? StreamUiState.Ready
        mainActivity?.setPipEligible(ready != null && !ready.hlsUrl.isNullOrBlank())
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findActivity()
            (activity as? MainActivity)?.setPipEligible(false)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val window = activity?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(isLandscape, isInPipMode) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (isLandscape || isInPipMode) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    when (val state = uiState) {
        is StreamUiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is StreamUiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = state.message, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBack) {
                    Text("Voltar")
                }
            }
        }

        is StreamUiState.Ready -> {
            LaunchedEffect(state.hlsUrl) {
                selectedQuality = null
                isAudioOnly = false
                availableQualities = emptyList()
            }

            val shortQualityBadge = remember(selectedQuality, isAudioOnly) {
                when {
                    isAudioOnly -> "Áudio"
                    selectedQuality != null -> selectedQuality!!.label.substringBefore(" ")
                    else -> "Auto"
                }
            }

            val isSeekableMedia = state.contentType == PlaybackContentType.VOD ||
                    state.contentType == PlaybackContentType.CLIP

            val videoSurfaceContent = remember(state.hlsUrl, state.bufferConfig, isSeekableMedia) {
                movableContentOf<Modifier, Boolean, Boolean> { playerModifier, inPip, landscapeMode ->
                    if (!state.hlsUrl.isNullOrBlank()) {
                        Box(modifier = playerModifier) {
                            CustomExoPlayerWithTrackSelector(
                                mediaUrl = state.hlsUrl,
                                bufferConfig = state.bufferConfig,
                                isSeekableVodOrClip = isSeekableMedia,
                                isInPipMode = inPip,
                                isLandscape = landscapeMode,
                                selectedQualityBadge = shortQualityBadge,
                                onQualitiesExtracted = { qualities -> availableQualities = qualities },
                                onPlayerReady = { player ->
                                    activePlayer = player
                                    player.setPlaybackSpeed(playbackSpeed)
                                },
                                onOpenSettingsSheet = { showSettingsSheet = true },
                                onToggleFullscreen = toggleFullscreen,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (isLoadingVodPlayback) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0x88000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.White)
                                }
                            }
                        }
                    } else {
                        OfflineChannelHeaderBanner(
                            channelLogin = state.channelLogin,
                            isLoadingPlayback = isLoadingVodPlayback,
                            onBack = onBack,
                            modifier = playerModifier
                        )
                    }
                }
            }

            if ((isInPipMode || isLandscape) && !state.hlsUrl.isNullOrBlank()) {
                videoSurfaceContent(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    isInPipMode,
                    isLandscape
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Área Superior 16:9 (Player ou Banner Offline)
                    videoSurfaceContent(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black),
                        false,
                        false
                    )

                    if (state.contentType == PlaybackContentType.VOD ||
                        state.contentType == PlaybackContentType.CLIP
                    ) {
                        Surface(
                            color = Color(0xFF1F1F23),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = state.activeMediaTitle.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (state.isChannelCurrentlyLive) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(
                                        onClick = { viewModel.returnToLiveStream() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LiveTv,
                                            contentDescription = null,
                                            tint = Color(0xFFFF4F4D),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Voltar à Live",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFFF4F4D),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. MENU DE ABAS ENTRE O VÍDEO E O CHAT (Chat Ao Vivo | VODs | Clips)
                    TabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        containerColor = Color(0xFF18181B),
                        contentColor = Color.White
                    ) {
                        ChannelContentTab.entries.forEach { tab ->
                            val badgeSuffix = when (tab) {
                                ChannelContentTab.CHAT -> ""
                                ChannelContentTab.VODS -> if (channelVods.isNotEmpty()) " (${channelVods.size})" else ""
                                ChannelContentTab.CLIPS -> if (channelClips.isNotEmpty()) " (${channelClips.size})" else ""
                            }
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = {
                                    Text(
                                        text = "${tab.title}$badgeSuffix",
                                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }

                    // 3. Área Inferior Dinâmica conforme a Aba Selecionada
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF0E0E10))
                    ) {
                        when (selectedTab) {
                            ChannelContentTab.CHAT -> {
                                LiveChatSection(
                                    channelName = state.channelLogin,
                                    messages = messages,
                                    thirdPartyEmotes = thirdPartyEmotes,
                                    onSendMessage = viewModel::sendChatMessage,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            ChannelContentTab.VODS -> {
                                ChannelVodsTabContent(
                                    vods = channelVods,
                                    selectedFilter = selectedVodFilter,
                                    isLoadingInitial = isLoadingVods,
                                    isLoadingMore = isLoadingMoreVods,
                                    canLoadMore = canLoadMoreVods,
                                    onSelectFilter = viewModel::onVodFilterSelected,
                                    onLoadMore = viewModel::loadMoreVods,
                                    onVodClick = viewModel::playVod,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            ChannelContentTab.CLIPS -> {
                                ChannelClipsTabContent(
                                    clips = channelClips,
                                    selectedFilter = selectedClipFilter,
                                    isLoadingInitial = isLoadingClips,
                                    isLoadingMore = isLoadingMoreClips,
                                    canLoadMore = canLoadMoreClips,
                                    onSelectFilter = viewModel::onClipFilterSelected,
                                    onLoadMore = viewModel::loadMoreClips,
                                    onClipClick = viewModel::playClip,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            if (showSettingsSheet && !isInPipMode) {
                ModalBottomSheet(
                    onDismissRequest = { showSettingsSheet = false }
                ) {
                    UnifiedPlayerSettingsSheetContent(
                        qualities = availableQualities,
                        selectedQuality = selectedQuality,
                        isAudioOnly = isAudioOnly,
                        currentSpeed = playbackSpeed,
                        onSelectAutoQuality = {
                            isAudioOnly = false
                            selectedQuality = null
                            activePlayer?.let { applyVideoQualityOverride(it, null, isAudioOnly = false) }
                            showSettingsSheet = false
                        },
                        onSelectAudioOnly = {
                            isAudioOnly = true
                            selectedQuality = null
                            activePlayer?.let { applyVideoQualityOverride(it, null, isAudioOnly = true) }
                            showSettingsSheet = false
                        },
                        onSelectQuality = { chosenQuality ->
                            isAudioOnly = false
                            selectedQuality = chosenQuality
                            activePlayer?.let { applyVideoQualityOverride(it, chosenQuality, isAudioOnly = false) }
                            showSettingsSheet = false
                        },
                        onSelectSpeed = { speed ->
                            playbackSpeed = speed
                            activePlayer?.setPlaybackSpeed(speed)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Barra horizontal de filtros de período (24 horas, 1 semana, 1 mês, Neste ano, Todos)
 * compartilhada pelas abas de VODs e Clips.
 */
@Composable
private fun MediaTimeFilterBar(
    selectedFilter: MediaTimeFilter,
    sortSubtitle: String,
    onSelectFilter: (MediaTimeFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141417))
            .padding(top = 8.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaTimeFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onSelectFilter(filter) },
                    label = {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }
        Text(
            text = sortSubtitle,
            style = MaterialTheme.typography.labelSmall,
            color = Color.LightGray,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
        )
        HorizontalDivider(color = Color(0xFF26262C), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun OfflineChannelHeaderBanner(
    channelLogin: String,
    isLoadingPlayback: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF18181B))
            .padding(16.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Voltar",
                tint = Color.White
            )
        }

        if (isLoadingPlayback) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = Color(0xFFA970FF),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "@$channelLogin está offline",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Escolha um VOD ou Clip nas abas abaixo para assistir sem anúncios",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
        }
    }
}

@Composable
private fun ChannelVodsTabContent(
    vods: List<ChannelVodUiModel>,
    selectedFilter: MediaTimeFilter,
    isLoadingInitial: Boolean,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    onSelectFilter: (MediaTimeFilter) -> Unit,
    onLoadMore: () -> Unit,
    onVodClick: (ChannelVodUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        MediaTimeFilterBar(
            selectedFilter = selectedFilter,
            sortSubtitle = "Ordenado por data (mais recentes primeiro)",
            onSelectFilter = onSelectFilter
        )

        when {
            isLoadingInitial && vods.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            vods.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhum VOD encontrado para o período \"${selectedFilter.label}\".",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = vods,
                        key = { "vod_${it.id}" }
                    ) { vod ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF18181B))
                                .clickable { onVodClick(vod) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box {
                                AsyncImage(
                                    model = vod.thumbnailUrl,
                                    contentDescription = vod.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(148.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.DarkGray)
                                )
                                if (vod.durationFormatted.isNotBlank()) {
                                    Text(
                                        text = vod.durationFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(Color(0xDD000000), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    color = Color(0xAA000000),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color(0xFFA970FF),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "VOD",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = vod.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${vod.viewCount} visualizações",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA970FF)
                                )
                                if (vod.createdAtFormatted.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Publicado em ${vod.createdAtFormatted}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }
                    }

                    // Botão "Ver mais" para carregar a próxima página de VODs
                    if (canLoadMore || isLoadingMore) {
                        item(key = "vods_load_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(26.dp))
                                } else {
                                    OutlinedButton(
                                        onClick = onLoadMore,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ExpandMore,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ver mais VODs")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelClipsTabContent(
    clips: List<ChannelClipUiModel>,
    selectedFilter: MediaTimeFilter,
    isLoadingInitial: Boolean,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    onSelectFilter: (MediaTimeFilter) -> Unit,
    onLoadMore: () -> Unit,
    onClipClick: (ChannelClipUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        MediaTimeFilterBar(
            selectedFilter = selectedFilter,
            sortSubtitle = "Ordenado por mais visualizações",
            onSelectFilter = onSelectFilter
        )

        when {
            isLoadingInitial && clips.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            clips.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhum Clip encontrado para o período \"${selectedFilter.label}\".",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = clips,
                        key = { "clip_${it.id}" }
                    ) { clip ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF18181B))
                                .clickable { onClipClick(clip) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box {
                                AsyncImage(
                                    model = clip.thumbnailUrl,
                                    contentDescription = clip.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(148.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.DarkGray)
                                )
                                if (clip.durationFormatted.isNotBlank()) {
                                    Text(
                                        text = clip.durationFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(Color(0xDD000000), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    color = Color(0xAA000000),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Movie,
                                            contentDescription = null,
                                            tint = Color(0xFFA970FF),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "CLIP",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = clip.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${clip.viewCount} visualizações • Clipado por ${clip.creatorName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFA970FF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (clip.createdAtFormatted.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = clip.createdAtFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }
                    }

                    // Botão "Ver mais" para carregar a próxima página de Clips
                    if (canLoadMore || isLoadingMore) {
                        item(key = "clips_load_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(26.dp))
                                } else {
                                    OutlinedButton(
                                        onClick = onLoadMore,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ExpandMore,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Ver mais Clips")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun CustomExoPlayerWithTrackSelector(
    mediaUrl: String,
    bufferConfig: BufferConfig,
    isSeekableVodOrClip: Boolean,
    isInPipMode: Boolean,
    isLandscape: Boolean,
    selectedQualityBadge: String,
    onQualitiesExtracted: (List<VideoQualityOption>) -> Unit,
    onPlayerReady: (ExoPlayer) -> Unit,
    onOpenSettingsSheet: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var areControlsVisible by remember { mutableStateOf(true) }
    var isVideoPlaying by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val exoPlayer = remember(mediaUrl, bufferConfig) {
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setForceHighestSupportedBitrate(false))
        }

        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                bufferConfig.minBufferMs,
                bufferConfig.maxBufferMs,
                bufferConfig.bufferForPlaybackMs,
                bufferConfig.bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .setAllowCrossProtocolRedirects(true)

        val isHlsStream = mediaUrl.contains(".m3u8", ignoreCase = true)

        val mediaSource = if (isHlsStream) {
            val mediaItem = MediaItem.Builder()
                .setUri(mediaUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
            HlsMediaSource.Factory(httpDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
        } else {
            val mediaItem = MediaItem.Builder()
                .setUri(mediaUrl)
                .setMimeType(MimeTypes.APPLICATION_MP4)
                .build()
            ProgressiveMediaSource.Factory(httpDataSourceFactory)
                .createMediaSource(mediaItem)
        }

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
            .apply {
                setMediaSource(mediaSource)
                playWhenReady = true
                prepare()
            }
    }

    LaunchedEffect(areControlsVisible, isVideoPlaying, isInPipMode) {
        if (areControlsVisible && isVideoPlaying && !isInPipMode) {
            delay(3000L)
            playerViewRef?.hideController()
            areControlsVisible = false
        }
    }

    DisposableEffect(exoPlayer) {
        onPlayerReady(exoPlayer)
        isVideoPlaying = exoPlayer.isPlaying

        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val extracted = extractVideoQualitiesFromTracks(tracks)
                if (extracted.isNotEmpty()) {
                    onQualitiesExtracted(extracted)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isVideoPlaying = isPlaying
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    playerViewRef = this
                    player = exoPlayer
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    controllerAutoShow = true
                    controllerHideOnTouch = true
                    controllerShowTimeoutMs = 3000
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)

                    setShowRewindButton(isSeekableVodOrClip)
                    setShowFastForwardButton(isSeekableVodOrClip)
                    setShowPreviousButton(false)
                    setShowNextButton(false)

                    findViewById<View>(androidx.media3.ui.R.id.exo_settings)?.let { nativeGear ->
                        (nativeGear.parent as? ViewGroup)?.removeView(nativeGear)
                    }
                    findViewById<View>(androidx.media3.ui.R.id.exo_fullscreen)?.let { nativeFs ->
                        (nativeFs.parent as? ViewGroup)?.removeView(nativeFs)
                    }
                    findViewById<View>(androidx.media3.ui.R.id.exo_minimal_fullscreen)?.let { nativeMinFs ->
                        (nativeMinFs.parent as? ViewGroup)?.removeView(nativeMinFs)
                    }

                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            areControlsVisible = (visibility == View.VISIBLE)
                        }
                    )
                }
            },
            update = { playerView ->
                playerViewRef = playerView
                playerView.player = exoPlayer
                playerView.useController = !isInPipMode
                playerView.setShowRewindButton(isSeekableVodOrClip)
                playerView.setShowFastForwardButton(isSeekableVodOrClip)
                if (isInPipMode) {
                    playerView.hideController()
                    areControlsVisible = false
                }
            }
        )

        AnimatedVisibility(
            visible = areControlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(if (isSeekableVodOrClip) Alignment.TopEnd else Alignment.BottomEnd)
                .padding(end = 8.dp, top = 8.dp, bottom = 6.dp)
        ) {
            Surface(
                color = Color(0xBB000000),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    TextButton(
                        onClick = {
                            playerViewRef?.hideController()
                            areControlsVisible = false
                            onOpenSettingsSheet()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurações do Player",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = selectedQualityBadge,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isLandscape) {
                                Icons.Default.FullscreenExit
                            } else {
                                Icons.Default.Fullscreen
                            },
                            contentDescription = if (isLandscape) {
                                "Voltar ao modo Retrato"
                            } else {
                                "Expandir para Tela Cheia"
                            },
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun extractVideoQualitiesFromTracks(tracks: Tracks): List<VideoQualityOption> {
    val options = mutableListOf<VideoQualityOption>()

    for (group in tracks.groups) {
        if (group.type == C.TRACK_TYPE_VIDEO) {
            val mediaTrackGroup = group.mediaTrackGroup
            for (i in 0 until mediaTrackGroup.length) {
                if (group.isTrackSupported(i)) {
                    val format = mediaTrackGroup.getFormat(i)
                    val height = format.height
                    val fps = if (format.frameRate > 0) format.frameRate.roundToInt() else 30
                    val bitrateMbps = if (format.bitrate > 0) {
                        String.format("%.1f Mbps", format.bitrate / 1_000_000f)
                    } else ""

                    if (height > 0) {
                        val fpsSuffix = if (fps >= 50) "$fps" else ""
                        val label = buildString {
                            append("${height}p$fpsSuffix")
                            if (bitrateMbps.isNotEmpty()) append(" ($bitrateMbps)")
                        }
                        options.add(
                            VideoQualityOption(
                                label = label,
                                height = height,
                                frameRate = fps,
                                bitrate = format.bitrate,
                                trackGroup = mediaTrackGroup,
                                trackIndex = i
                            )
                        )
                    }
                }
            }
        }
    }
    return options.sortedWith(
        compareByDescending<VideoQualityOption> { it.height }
            .thenByDescending { it.frameRate }
            .thenByDescending { it.bitrate }
    )
}

private fun applyVideoQualityOverride(
    exoPlayer: ExoPlayer,
    quality: VideoQualityOption?,
    isAudioOnly: Boolean
) {
    val builder = exoPlayer.trackSelectionParameters.buildUpon()
    if (isAudioOnly) {
        builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        builder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
    } else {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
        if (quality == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        } else {
            builder.setOverrideForType(
                TrackSelectionOverride(quality.trackGroup, listOf(quality.trackIndex))
            )
        }
    }
    exoPlayer.trackSelectionParameters = builder.build()
}

@Composable
private fun UnifiedPlayerSettingsSheetContent(
    qualities: List<VideoQualityOption>,
    selectedQuality: VideoQualityOption?,
    isAudioOnly: Boolean,
    currentSpeed: Float,
    onSelectAutoQuality: () -> Unit,
    onSelectAudioOnly: () -> Unit,
    onSelectQuality: (VideoQualityOption) -> Unit,
    onSelectSpeed: (Float) -> Unit
) {
    val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Velocidade de Reprodução",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            speeds.forEach { speed ->
                FilterChip(
                    selected = currentSpeed == speed,
                    onClick = { onSelectSpeed(speed) },
                    label = { Text("${speed}x") }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()

        Text(
            text = "Qualidade da Transmissão",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        ListItem(
            headlineContent = { Text("Automático (Adaptativo)") },
            trailingContent = {
                RadioButton(
                    selected = !isAudioOnly && selectedQuality == null,
                    onClick = onSelectAutoQuality
                )
            },
            modifier = Modifier.clickable(onClick = onSelectAutoQuality)
        )

        ListItem(
            headlineContent = { Text("Apenas Áudio (Economia de Dados)") },
            trailingContent = {
                RadioButton(
                    selected = isAudioOnly,
                    onClick = onSelectAudioOnly
                )
            },
            modifier = Modifier.clickable(onClick = onSelectAudioOnly)
        )

        qualities.forEach { option ->
            val isSelected = !isAudioOnly &&
                    selectedQuality?.trackIndex == option.trackIndex &&
                    selectedQuality.height == option.height
            ListItem(
                headlineContent = { Text(option.label) },
                trailingContent = {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelectQuality(option) }
                    )
                },
                modifier = Modifier.clickable { onSelectQuality(option) }
            )
        }
    }
}

@Composable
fun LiveChatSection(
    channelName: String,
    messages: List<ChatMessage>,
    thirdPartyEmotes: Map<String, String>,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val animatedImageLoader = rememberAnimatedEmoteImageLoader()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .background(Color(0xFF0E0E10))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CHAT AO VIVO • #$channelName",
                style = MaterialTheme.typography.labelMedium,
                color = Color.LightGray
            )
            if (thirdPartyEmotes.isNotEmpty()) {
                Text(
                    text = "7TV/BTTV/FFZ (${thirdPartyEmotes.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF9146FF)
                )
            }
        }
        HorizontalDivider(color = Color(0xFF26262C))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageItem(
                    message = msg,
                    thirdPartyEmotes = thirdPartyEmotes,
                    imageLoader = animatedImageLoader
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Envie uma mensagem...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    onSendMessage(inputText)
                    inputText = ""
                }
            ) {
                Text("Enviar")
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
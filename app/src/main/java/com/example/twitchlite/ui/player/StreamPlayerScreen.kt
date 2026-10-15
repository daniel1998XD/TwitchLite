package com.example.twitchlite.ui.player

import kotlinx.coroutines.delay
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.twitchlite.MainActivity
import com.example.twitchlite.domain.model.BufferConfig
import com.example.twitchlite.domain.model.ChatMessage
import com.example.twitchlite.domain.model.VideoQualityOption
import kotlin.math.roundToInt

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

    var availableQualities by remember { mutableStateOf<List<VideoQualityOption>>(emptyList()) }
    var selectedQuality by remember { mutableStateOf<VideoQualityOption?>(null) }
    var isAudioOnly by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(channelLogin) {
        viewModel.loadStream(channelLogin)
    }

    LaunchedEffect(autoClaimNotice) {
        val notice = autoClaimNotice
        if (!notice.isNullOrBlank()) {
            Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
            viewModel.consumeAutoClaimNotification()
        }
    }

    // TOGGLE DE TELA CHEIA CORRIGIDO:
    // Consulta a orientação real diretamente na Activity no momento do clique,
    // evitando que o movableContentOf retenha um valor booleano antigo.
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

    // Botão Voltar do Android: se estiver em Paisagem, volta para Retrato; se já estiver em Retrato, sai da live
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
        mainActivity?.setPipEligible(uiState is StreamUiState.Ready)
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
            val shortQualityBadge = remember(selectedQuality, isAudioOnly) {
                when {
                    isAudioOnly -> "Áudio"
                    selectedQuality != null -> selectedQuality!!.label.substringBefore(" ")
                    else -> "Auto"
                }
            }

            val videoSurfaceContent = remember(state.hlsUrl, state.bufferConfig) {
                movableContentOf<Modifier, Boolean, Boolean> { playerModifier, inPip, landscapeMode ->
                    CustomExoPlayerWithTrackSelector(
                        hlsUrl = state.hlsUrl,
                        bufferConfig = state.bufferConfig,
                        isInPipMode = inPip,
                        isLandscape = landscapeMode,
                        selectedQualityBadge = shortQualityBadge,
                        onQualitiesExtracted = { qualities -> availableQualities = qualities },
                        onPlayerReady = { player -> activePlayer = player },
                        onOpenSettingsSheet = { showSettingsSheet = true },
                        onToggleFullscreen = toggleFullscreen,
                        modifier = playerModifier
                    )
                }
            }

            if (isInPipMode || isLandscape) {
                videoSurfaceContent(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    isInPipMode,
                    isLandscape
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    videoSurfaceContent(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black),
                        false,
                        false
                    )

                    LiveChatSection(
                        channelName = state.channelLogin,
                        messages = messages,
                        thirdPartyEmotes = thirdPartyEmotes,
                        onSendMessage = viewModel::sendChatMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }

            // Menu Unificado (Qualidade HLS + Apenas Áudio + Velocidade de Reprodução)
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

@OptIn(UnstableApi::class)
@Composable
fun CustomExoPlayerWithTrackSelector(
    hlsUrl: String,
    bufferConfig: BufferConfig,
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

    val exoPlayer = remember(hlsUrl, bufferConfig) {
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

        val mediaItem = MediaItem.Builder()
            .setUri(hlsUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        val hlsMediaSource = HlsMediaSource.Factory(httpDataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaSource(hlsMediaSource)
                playWhenReady = true
                prepare()
            }
    }

    // Auto-hide garantido no Compose + sincronizado com o PlayerView:
    // Assim que os controles estiverem visíveis e a live estiver rodando (isPlaying),
    // conta 3 segundos e esconde tanto a barra nativa do Media3 quanto o overlay Compose.
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

                    // REMOVE a engrenagem nativa (exo_settings) e o botão fullscreen nativo (exo_fullscreen)
                    // da árvore interna do PlayerView para que exista APENAS a nossa barra no canto inferior direito.
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
                if (isInPipMode) {
                    playerView.hideController()
                    areControlsVisible = false
                }
            }
        )

        // Controles Unificados (Engrenagem + Fullscreen) fixos no CANTO INFERIOR DIREITO
        // e sincronizados com o toque na tela
        AnimatedVisibility(
            visible = areControlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 6.dp)
        ) {
            Surface(
                color = Color(0xBB000000),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    // 1. Engrenagem Unificada (Qualidade + Áudio + Velocidade)
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

                    // 2. Botão Toggle de Tela Cheia (Retrato <-> Paisagem) imediatamente ao lado direito
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
        // Desativa a renderização da trilha de vídeo e mantém apenas o áudio da live
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
        // Seção 1: Velocidade de Reprodução (substitui o menu nativo do ExoPlayer)
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

        // Seção 2: Qualidade de Vídeo + Modo Apenas Áudio
        Text(
            text = "Qualidade da Transmissão",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        // Opção 1: Automático (Adaptive)
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

        // Opção 2: Apenas Áudio (Economia de banda)
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

        // Opções do Master Playlist (.m3u8)
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
package com.example.twitchlite.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
    var showQualitySheet by remember { mutableStateOf(false) }
    var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(channelLogin) {
        viewModel.loadStream(channelLogin)
    }

    // Exibe aviso discreto caso o Auto-Claim tenha resgatado Pontos ou Drops
    LaunchedEffect(autoClaimNotice) {
        val notice = autoClaimNotice
        if (!notice.isNullOrBlank()) {
            Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
            viewModel.consumeAutoClaimNotification()
        }
    }

    // Função para alternar entre Modo Retrato e Paisagem (Fullscreen)
    val toggleFullscreen: () -> Unit = {
        val activity = context.findActivity()
        if (activity != null) {
            if (isLandscape) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    // Se estiver em Fullscreen (Landscape), o botão Voltar retorna ao modo Retrato primeiro
    BackHandler {
        val activity = context.findActivity()
        if (isLandscape && activity != null) {
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
            // Badge curto da qualidade selecionada (ex: "1080p60" ou "Auto")
            val shortQualityBadge = remember(selectedQuality) {
                selectedQuality?.label?.substringBefore(" ") ?: "Auto"
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
                        onOpenQualitySheet = { showQualitySheet = true },
                        onToggleFullscreen = toggleFullscreen,
                        modifier = playerModifier
                    )
                }
            }

            if (isInPipMode || isLandscape) {
                // MODO FULLSCREEN (PAISAGEM) OU PiP
                videoSurfaceContent(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    isInPipMode,
                    isLandscape
                )
            } else {
                // MODO RETRATO: Player 16:9 no topo + Chat com Emotes (Twitch + 7TV + BTTV + FFZ)
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

            if (showQualitySheet && !isInPipMode) {
                ModalBottomSheet(
                    onDismissRequest = { showQualitySheet = false }
                ) {
                    QualitySelectionSheetContent(
                        qualities = availableQualities,
                        selectedQuality = selectedQuality,
                        onSelectQuality = { chosenQuality ->
                            selectedQuality = chosenQuality
                            activePlayer?.let { player ->
                                applyVideoQualityOverride(player, chosenQuality)
                            }
                            showQualitySheet = false
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
    onOpenQualitySheet: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Estado sincronizado com a visibilidade dos controles nativos do ExoPlayer
    var areControlsVisible by remember { mutableStateOf(true) }

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

    DisposableEffect(exoPlayer) {
        onPlayerReady(exoPlayer)

        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val extracted = extractVideoQualitiesFromTracks(tracks)
                if (extracted.isNotEmpty()) {
                    onQualitiesExtracted(extracted)
                }
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
                    player = exoPlayer
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 3000

                    // Sincroniza a visibilidade da sobreposição Compose (Qualidade + Fullscreen)
                    // com o toque na tela do PlayerView
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            areControlsVisible = (visibility == View.VISIBLE)
                        }
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.useController = !isInPipMode
                if (isInPipMode) {
                    playerView.hideController()
                    areControlsVisible = false
                }
            }
        )

        // Barra superior direita sincronizada com os controles do ExoPlayer
        AnimatedVisibility(
            visible = areControlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Surface(
                color = Color(0xAA000000),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    // 1. Botão de Qualidade (Engrenagem + Resolução atual)
                    TextButton(
                        onClick = onOpenQualitySheet,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Qualidade",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = selectedQualityBadge,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 2. Botão de Tela Cheia (Fullscreen) imediatamente ao lado direito da engrenagem
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isLandscape) {
                                Icons.Default.FullscreenExit
                            } else {
                                Icons.Default.Fullscreen
                            },
                            contentDescription = if (isLandscape) "Sair da Tela Cheia" else "Tela Cheia",
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
    quality: VideoQualityOption?
) {
    val builder = exoPlayer.trackSelectionParameters.buildUpon()
    if (quality == null) {
        builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    } else {
        builder.setOverrideForType(
            TrackSelectionOverride(quality.trackGroup, listOf(quality.trackIndex))
        )
    }
    exoPlayer.trackSelectionParameters = builder.build()
}

@Composable
private fun QualitySelectionSheetContent(
    qualities: List<VideoQualityOption>,
    selectedQuality: VideoQualityOption?,
    onSelectQuality: (VideoQualityOption?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Qualidade da Transmissão",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Automático (Adaptativo)") },
            trailingContent = {
                RadioButton(
                    selected = selectedQuality == null,
                    onClick = { onSelectQuality(null) }
                )
            },
            modifier = Modifier.clickable { onSelectQuality(null) }
        )

        qualities.forEach { option ->
            val isSelected = selectedQuality?.trackIndex == option.trackIndex &&
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
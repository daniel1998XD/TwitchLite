package com.example.twitchlite.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.example.twitchlite.data.remote.dto.HelixStreamDto
import com.example.twitchlite.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowedStreamsScreen(
    viewModel: HomeViewModel,
    onStreamClick: (channelLogin: String) -> Unit,
    onCategoryClick: (categoryName: String) -> Unit = {}
) {
    val lazyPagingStreams: LazyPagingItems<HelixStreamDto> =
        viewModel.followedStreamsPagingFlow.collectAsLazyPagingItems()
    val authStatus by viewModel.authStatusMessage.collectAsStateWithLifecycle()
    val homeUiState by viewModel.uiState.collectAsStateWithLifecycle()

    var directChannelInput by remember { mutableStateOf("") }
    var showAuthDialog by remember { mutableStateOf(false) }

    // Tarefa 3: Estado local para controlar o limite de 10 canais seguidos
    var isChannelsExpanded by rememberSaveable { mutableStateOf(false) }

    // Sincroniza o fallback de categorias via GQL caso os canais carreguem
    LaunchedEffect(lazyPagingStreams.itemCount) {
        if (lazyPagingStreams.itemCount > 0 && homeUiState.followedCategories.isEmpty()) {
            val loadedStreams = lazyPagingStreams.itemSnapshotList.items
            viewModel.syncCategoriesFromFollowedStreamsFallback(loadedStreams)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TwitchLite • Ao Vivo", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            lazyPagingStreams.refresh()
                            viewModel.loadFollowedCategories()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar")
                    }
                    IconButton(onClick = { showAuthDialog = true }) {
                        Icon(Icons.Default.Key, contentDescription = "Configurar Login OAuth")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Card de acesso rápido para testar qualquer canal via GQL/Usher sem depender de login
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Abrir Canal Direto (Extração Nativa GQL/Usher)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = directChannelInput,
                            onValueChange = { directChannelInput = it },
                            placeholder = { Text("Ex: gaules, alanzoka...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (directChannelInput.isNotBlank()) {
                                    onStreamClick(directChannelInput.trim())
                                }
                            }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Assistir")
                        }
                    }
                }
            }

            if (!authStatus.isNullOrBlank()) {
                Text(
                    text = authStatus!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val totalStreams = lazyPagingStreams.itemCount
            val visibleStreamsCount = if (isChannelsExpanded) {
                totalStreams
            } else {
                minOf(totalStreams, 10)
            }

            // Feed Principal (Canais Seguidos + Categorias Seguidas)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cabeçalho da Seção: Canais Seguidos
                item(key = "header_followed_channels") {
                    Text(
                        text = "Canais Seguidos ao Vivo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                // Tarefa 3: Lista de Canais Seguidos limitada a 10 por padrão
                items(
                    count = visibleStreamsCount,
                    key = { index -> lazyPagingStreams.peek(index)?.id ?: index }
                ) { index ->
                    val stream = lazyPagingStreams[index] ?: return@items
                    StreamRowItem(
                        stream = stream,
                        onClick = { onStreamClick(stream.userLogin) }
                    )
                }

                // Botão "Ver mais" / "Ver menos" quando há mais de 10 canais
                if (totalStreams > 10) {
                    item(key = "toggle_channels_expand") {
                        OutlinedButton(
                            onClick = { isChannelsExpanded = !isChannelsExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (isChannelsExpanded) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isChannelsExpanded) "Ver menos" else "Ver mais"
                            )
                        }
                    }
                }

                // Estado inicial (Refresh) do Paging 3
                when (val refreshState = lazyPagingStreams.loadState.refresh) {
                    is LoadState.Loading -> {
                        item(key = "channels_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is LoadState.Error -> {
                        item(key = "channels_error") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Para listar seus canais seguidos da Helix API:",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = refreshState.error.localizedMessage ?: "Configure o OAuth",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { showAuthDialog = true }) {
                                        Text("Configurar Token OAuth")
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }

                // Estado de paginação (Append) — exibido apenas quando a lista está expandida
                if (isChannelsExpanded) {
                    when (val appendState = lazyPagingStreams.loadState.append) {
                        is LoadState.Loading -> {
                            item(key = "channels_append_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                        is LoadState.Error -> {
                            item(key = "channels_append_error") {
                                TextButton(
                                    onClick = { lazyPagingStreams.retry() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Erro ao carregar mais (${appendState.error.localizedMessage}). Tocar para tentar novamente.")
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                // ==================== TAREFA 4: CATEGORIAS SEGUIDAS ====================
                if (homeUiState.followedCategories.isNotEmpty() || homeUiState.isLoadingCategories) {
                    item(key = "followed_categories_section") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                            Text(
                                text = "Categorias Seguidas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            if (homeUiState.isLoadingCategories && homeUiState.followedCategories.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(
                                        items = homeUiState.followedCategories,
                                        key = { it.id }
                                    ) { category ->
                                        FollowedCategoryCard(
                                            category = category,
                                            onClick = { onCategoryClick(category.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAuthDialog) {
        OAuthConfigDialog(
            onDismiss = { showAuthDialog = false },
            onSaveToken = { token, clientId ->
                viewModel.authenticateWithToken(token, clientId)
                showAuthDialog = false
            }
        )
    }
}

@Composable
private fun FollowedCategoryCard(
    category: FollowedCategoryUiModel,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = category.boxArtUrl,
            contentDescription = category.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (category.viewersCount > 0) {
            Text(
                text = "${formatViewerCount(category.viewersCount)} espectadores",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatViewerCount(count: Int): String {
    return if (count >= 1000) {
        String.format("%.1f mil", count / 1000f)
    } else {
        count.toString()
    }
}

@Composable
private fun StreamRowItem(
    stream: HelixStreamDto,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box {
            AsyncImage(
                model = stream.getFormattedThumbnail(),
                contentDescription = stream.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(148.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.DarkGray)
            )
            Text(
                text = "AO VIVO • ${stream.viewerCount}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stream.userName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stream.gameName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stream.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OAuthConfigDialog(
    onDismiss: () -> Unit,
    onSaveToken: (token: String, clientId: String) -> Unit
) {
    val context = LocalContext.current
    var clientIdInput by remember { mutableStateOf(AppContainer.TWITCH_CLIENT_ID) }
    var tokenInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Login OAuth 2.0 (Twitch Helix)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Informe o Client-ID do seu app na Twitch e autentique via navegador ou cole seu Access Token (escopos: user:read:follows chat:read chat:edit):",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = clientIdInput,
                    onValueChange = { clientIdInput = it },
                    label = { Text("Client-ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("Access Token OAuth") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        AppContainer.TWITCH_CLIENT_ID = clientIdInput.trim()
                        val authUri = Uri.parse("https://id.twitch.tv/oauth2/authorize")
                            .buildUpon()
                            .appendQueryParameter("client_id", clientIdInput.trim())
                            .appendQueryParameter("redirect_uri", AppContainer.OAUTH_REDIRECT_URI)
                            .appendQueryParameter("response_type", "token")
                            .appendQueryParameter("scope", "user:read:follows chat:read chat:edit")
                            .build()
                        context.startActivity(Intent(Intent.ACTION_VIEW, authUri))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Abrir Login no Navegador")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveToken(tokenInput, clientIdInput) },
                enabled = tokenInput.isNotBlank() && clientIdInput.isNotBlank()
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
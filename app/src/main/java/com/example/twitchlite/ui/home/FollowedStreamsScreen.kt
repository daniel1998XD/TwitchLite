package com.example.twitchlite.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
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
    onStreamClick: (channelLogin: String) -> Unit
) {
    val lazyPagingStreams: LazyPagingItems<HelixStreamDto> =
        viewModel.followedStreamsPagingFlow.collectAsLazyPagingItems()
    val authStatus by viewModel.authStatusMessage.collectAsStateWithLifecycle()
    val homeUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

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
            // Barra de Pesquisa Oficial (Input + Botão de Lupa ao lado, igual à Twitch Web)
            TwitchStyleSearchBar(
                query = homeUiState.searchQuery,
                isSearching = homeUiState.isFetchingAutocomplete,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = {
                    viewModel.clearSearch()
                    focusManager.clearFocus()
                },
                onSubmitSearch = {
                    focusManager.clearFocus()
                    viewModel.executeFullSearch()
                }
            )

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

            Box(modifier = Modifier.fillMaxSize()) {
                // ETAPA 1: Dropdown de Autocomplete na Tela Inicial (enquanto a janela cheia não está aberta)
                if (!homeUiState.isSearchResultsOpen &&
                    homeUiState.showAutocompleteDropdown &&
                    homeUiState.searchQuery.trim().isNotEmpty()
                ) {
                    SearchAutocompleteDropdown(
                        query = homeUiState.searchQuery.trim(),
                        isLoading = homeUiState.isFetchingAutocomplete,
                        categories = homeUiState.autocompleteCategories,
                        channels = homeUiState.autocompleteChannels,
                        onCategoryClick = { category ->
                            focusManager.clearFocus()
                            viewModel.dismissAutocompleteDropdown()
                            viewModel.openCategoryStreams(category)
                        },
                        onChannelClick = { channelLogin ->
                            focusManager.clearFocus()
                            viewModel.dismissAutocompleteDropdown()
                            onStreamClick(channelLogin)
                        },
                        onExecuteFullSearch = { selectedQuery ->
                            focusManager.clearFocus()
                            viewModel.executeFullSearch(selectedQuery)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .zIndex(10f)
                    )
                }

                // Feed Principal (Canais Seguidos + Categorias Seguidas)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                                onClick = { viewModel.openCategoryStreams(category) }
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
    }

    // ETAPA 2: Tela Completa de Resultados da Pesquisa (com o Dropdown de Autocomplete integrado!)
    if (homeUiState.isSearchResultsOpen) {
        FullSearchResultsWindow(
            searchQuery = homeUiState.searchQuery,
            submittedQuery = homeUiState.submittedSearchQuery,
            isLoading = homeUiState.isSearchingFullResults,
            errorMessage = homeUiState.fullSearchError,
            channels = homeUiState.fullSearchLiveChannels,
            categories = homeUiState.fullSearchCategories,
            showAutocompleteDropdown = homeUiState.showAutocompleteDropdown,
            isFetchingAutocomplete = homeUiState.isFetchingAutocomplete,
            autocompleteCategories = homeUiState.autocompleteCategories,
            autocompleteChannels = homeUiState.autocompleteChannels,
            onQueryChange = viewModel::onSearchQueryChange,
            onClearQuery = viewModel::clearSearch,
            onSubmitSearch = { queryToSubmit ->
                viewModel.executeFullSearch(queryToSubmit)
            },
            onDismissAutocomplete = viewModel::dismissAutocompleteDropdown,
            onDismiss = { viewModel.closeFullSearchResults() },
            onChannelClick = { channelLogin ->
                viewModel.dismissAutocompleteDropdown()
                viewModel.closeFullSearchResults()
                onStreamClick(channelLogin)
            },
            onCategoryClick = { category ->
                viewModel.dismissAutocompleteDropdown()
                viewModel.openCategoryStreams(category)
            }
        )
    }

    // Janela de Transmissões de uma Categoria Clicada (fica acima da busca se aberta a partir dela)
    val selectedCategory = homeUiState.selectedCategory
    if (selectedCategory != null) {
        CategoryStreamsWindow(
            category = selectedCategory,
            streams = homeUiState.categoryStreams,
            isLoading = homeUiState.isLoadingCategoryStreams,
            errorMessage = homeUiState.categoryStreamsError,
            onDismiss = { viewModel.closeCategoryStreams() },
            onRefresh = { viewModel.openCategoryStreams(selectedCategory) },
            onStreamClick = { channelLogin ->
                viewModel.closeCategoryStreams()
                viewModel.closeFullSearchResults()
                onStreamClick(channelLogin)
            }
        )
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
private fun TwitchStyleSearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmitSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Pesquisar canais ou categorias...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpar"
                            )
                        }
                    }
                }
            },
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
            modifier = Modifier.weight(1f)
        )

        // Botão de Lupa ao lado direito do input (idêntico à barra da Twitch nas imagens)
        FilledIconButton(
            onClick = onSubmitSearch,
            enabled = query.trim().isNotEmpty(),
            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            modifier = Modifier
                .height(56.dp)
                .width(52.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar"
            )
        }
    }
}

/**
 * Dropdown de Autocomplete igual à Imagem 2:
 * - Categorias no topo (Capa 3:4 + Nome + Subtítulo "Categoria")
 * - Canais abaixo (Avatar circular + Nome + Selo + Subtítulo "Canal")
 * - Linha com Lupa para abrir a Tela Completa de Resultados da Pesquisa
 * - Linha "Ir para [query]" no rodapé
 */
@Composable
private fun SearchAutocompleteDropdown(
    query: String,
    isLoading: Boolean,
    categories: List<FollowedCategoryUiModel>,
    channels: List<SearchChannelUiModel>,
    onCategoryClick: (FollowedCategoryUiModel) -> Unit,
    onChannelClick: (String) -> Unit,
    onExecuteFullSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.heightIn(max = 440.dp),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF18181B)
        )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            if (isLoading && categories.isEmpty() && channels.isEmpty()) {
                item(key = "autocomplete_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    }
                }
            }

            // 1. Categorias sugeridas (ex: Black Desert -> "Categoria")
            items(
                items = categories,
                key = { "auto_cat_${it.id}" }
            ) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategoryClick(category) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = category.boxArtUrl,
                        contentDescription = category.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(34.dp)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.DarkGray)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Categoria",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }
            }

            // 2. Canais sugeridos (ex: BlackDesertGame -> "Canal")
            items(
                items = channels,
                key = { "auto_ch_${it.id}" }
            ) { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChannelClick(channel.login) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = channel.avatarUrl,
                        contentDescription = channel.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = channel.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (channel.isPartner) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verificado",
                                    tint = Color(0xFF9146FF),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Text(
                            text = if (channel.isLive) {
                                "Canal • AO VIVO (${formatViewerCount(channel.viewersCount)})"
                            } else {
                                "Canal"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (channel.isLive) Color(0xFFFF4F4D) else Color.LightGray
                        )
                    }
                }
            }

            // 3. Linha com ícone de lupa para abrir a Tela Completa de Pesquisa (Imagem 1)
            item(key = "autocomplete_full_search") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExecuteFullSearch(query) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(Ver todos os canais e categorias)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }

            // 4. Rodapé "Ir para [query]"
            item(key = "autocomplete_goto") {
                Text(
                    text = "Ir para $query",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.LightGray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChannelClick(query.lowercase()) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * Tela Completa de Resultados da Pesquisa (igual à Imagem 1 do site oficial da Twitch),
 * agora também com o Dropdown de Autocomplete ativo quando o usuário digita uma nova busca!
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullSearchResultsWindow(
    searchQuery: String,
    submittedQuery: String,
    isLoading: Boolean,
    errorMessage: String?,
    channels: List<SearchChannelUiModel>,
    categories: List<FollowedCategoryUiModel>,
    showAutocompleteDropdown: Boolean,
    isFetchingAutocomplete: Boolean,
    autocompleteCategories: List<FollowedCategoryUiModel>,
    autocompleteChannels: List<SearchChannelUiModel>,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSubmitSearch: (String) -> Unit,
    onDismissAutocomplete: () -> Unit,
    onDismiss: () -> Unit,
    onChannelClick: (String) -> Unit,
    onCategoryClick: (FollowedCategoryUiModel) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var showAllChannels by rememberSaveable(submittedQuery) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF0E0E10),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF18181B),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Voltar"
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "Resultados para \"$submittedQuery\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                TwitchStyleSearchBar(
                    query = searchQuery,
                    isSearching = isLoading || isFetchingAutocomplete,
                    onQueryChange = onQueryChange,
                    onClear = {
                        onClearQuery()
                        focusManager.clearFocus()
                    },
                    onSubmitSearch = {
                        focusManager.clearFocus()
                        onSubmitSearch(searchQuery)
                    }
                )

                // Box que empilha o Dropdown de Autocomplete sobre os resultados da busca atual
                Box(modifier = Modifier.fillMaxSize()) {
                    if (showAutocompleteDropdown && searchQuery.trim().isNotEmpty()) {
                        SearchAutocompleteDropdown(
                            query = searchQuery.trim(),
                            isLoading = isFetchingAutocomplete,
                            categories = autocompleteCategories,
                            channels = autocompleteChannels,
                            onCategoryClick = { category ->
                                focusManager.clearFocus()
                                onDismissAutocomplete()
                                onCategoryClick(category)
                            },
                            onChannelClick = { channelLogin ->
                                focusManager.clearFocus()
                                onDismissAutocomplete()
                                onChannelClick(channelLogin)
                            },
                            onExecuteFullSearch = { selectedQuery ->
                                focusManager.clearFocus()
                                onDismissAutocomplete()
                                onSubmitSearch(selectedQuery)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .zIndex(10f)
                        )
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (!errorMessage.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        val visibleChannels = if (showAllChannels) {
                            channels
                        } else {
                            channels.take(6)
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // ==================== SEÇÃO 1: CANAIS (Estilo Imagem 1) ====================
                            if (channels.isNotEmpty()) {
                                item(key = "full_search_channels_header") {
                                    Text(
                                        text = "Canais",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                items(
                                    items = visibleChannels,
                                    key = { "full_ch_${it.login}" }
                                ) { channel ->
                                    SearchChannelResultItem(
                                        channel = channel,
                                        onClick = { onChannelClick(channel.login) }
                                    )
                                }

                                if (channels.size > 6) {
                                    val remaining = channels.size - 6
                                    item(key = "full_search_channels_expand") {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            HorizontalDivider(color = Color(0xFF26262C))
                                            TextButton(
                                                onClick = { showAllChannels = !showAllChannels }
                                            ) {
                                                Text(
                                                    text = if (showAllChannels) {
                                                        "Mostrar menos canais"
                                                    } else {
                                                        "Mostrar mais $remaining canais"
                                                    },
                                                    color = Color(0xFFA970FF),
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = if (showAllChannels) {
                                                        Icons.Default.ExpandLess
                                                    } else {
                                                        Icons.Default.ExpandMore
                                                    },
                                                    contentDescription = null,
                                                    tint = Color(0xFFA970FF)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // ==================== SEÇÃO 2: CATEGORIAS (Estilo Imagem 1) ====================
                            if (categories.isNotEmpty()) {
                                item(key = "full_search_categories_header") {
                                    Column {
                                        if (channels.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        Text(
                                            text = "Categorias",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }

                                items(
                                    items = categories,
                                    key = { "full_cat_${it.id}" }
                                ) { category ->
                                    SearchCategoryResultRow(
                                        category = category,
                                        onClick = { onCategoryClick(category) }
                                    )
                                }
                            }

                            if (channels.isEmpty() && categories.isEmpty()) {
                                item(key = "full_search_empty") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(40.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Nenhum canal ou categoria encontrada para \"$submittedQuery\".",
                                            color = Color.LightGray
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
}

@Composable
private fun SearchChannelResultItem(
    channel: SearchChannelUiModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (channel.isLive) {
            Box {
                AsyncImage(
                    model = channel.thumbnailUrl,
                    contentDescription = channel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(156.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.DarkGray)
                )
                Surface(
                    color = Color(0xFFE91916),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "AO VIVO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .width(156.dp)
                    .height(88.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = channel.avatarUrl,
                    contentDescription = channel.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.isPartner) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verificado",
                        tint = Color(0xFF9146FF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (channel.gameName.isNotBlank()) {
                Text(
                    text = channel.gameName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (channel.isLive) {
                Text(
                    text = "${formatViewerCount(channel.viewersCount)} espectadores",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            } else {
                Text(
                    text = "Canal offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            if (channel.title.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFDEDEE3),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SearchCategoryResultRow(
    category: FollowedCategoryUiModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = category.boxArtUrl,
            contentDescription = category.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(72.dp)
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.DarkGray)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (category.viewersCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatViewerCount(category.viewersCount)} espectadores",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryStreamsWindow(
    category: FollowedCategoryUiModel,
    streams: List<CategoryStreamUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onStreamClick: (channelLogin: String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = category.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Voltar"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Atualizar transmissões"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "category_header") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = category.boxArtUrl,
                                contentDescription = category.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(76.dp)
                                    .aspectRatio(3f / 4f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = category.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (category.viewersCount > 0) {
                                    Text(
                                        text = "${formatViewerCount(category.viewersCount)} espectadores na categoria",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Canais ao vivo ordenados por espectadores",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                when {
                    isLoading -> {
                        item(key = "category_streams_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    !errorMessage.isNullOrBlank() -> {
                        item(key = "category_streams_error") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = errorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = onRefresh) {
                                    Text("Tentar Novamente")
                                }
                            }
                        }
                    }

                    streams.isEmpty() -> {
                        item(key = "category_streams_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nenhuma transmissão ao vivo encontrada para esta categoria no momento.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    else -> {
                        items(
                            items = streams,
                            key = { it.id }
                        ) { stream ->
                            CategoryStreamRowItem(
                                stream = stream,
                                onClick = { onStreamClick(stream.userLogin) }
                            )
                        }
                    }
                }
            }
        }
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
    StreamItemContent(
        thumbnailUrl = stream.getFormattedThumbnail(),
        viewerCount = stream.viewerCount,
        userName = stream.userName,
        gameName = stream.gameName,
        title = stream.title,
        onClick = onClick
    )
}

@Composable
private fun CategoryStreamRowItem(
    stream: CategoryStreamUiModel,
    onClick: () -> Unit
) {
    StreamItemContent(
        thumbnailUrl = stream.thumbnailUrl,
        viewerCount = stream.viewerCount,
        userName = stream.userName,
        gameName = stream.gameName,
        title = stream.title,
        onClick = onClick
    )
}

@Composable
private fun StreamItemContent(
    thumbnailUrl: String,
    viewerCount: Int,
    userName: String,
    gameName: String,
    title: String,
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
                model = thumbnailUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(148.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.DarkGray)
            )
            Text(
                text = "AO VIVO • $viewerCount",
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
                text = userName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = gameName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
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
package com.example.twitchlite.ui.home

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.twitchlite.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TwitchLoginWebViewDialog(
    clientId: String,
    redirectUri: String = AppContainer.OAUTH_REDIRECT_URI,
    scopes: String = "user:read:follows chat:read chat:edit",
    onTokenExtracted: (accessToken: String) -> Unit,
    onDismiss: () -> Unit
) {
    val authUrl = remember(clientId, redirectUri, scopes) {
        Uri.parse("https://id.twitch.tv/oauth2/authorize")
            .buildUpon()
            .appendQueryParameter("client_id", clientId.trim())
            .appendQueryParameter("redirect_uri", redirectUri.trim())
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("force_verify", "true")
            .build()
            .toString()
    }

    var loadProgress by remember { mutableIntStateOf(0) }
    var hasExtractedToken by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Função centralizada que verifica se a URL contém #access_token=
    val tryInterceptToken: (String?) -> Boolean = { url ->
        if (!hasExtractedToken && !url.isNullOrBlank()) {
            val extractedToken = extractAccessTokenFromUrl(url, redirectUri)
            if (!extractedToken.isNullOrBlank()) {
                hasExtractedToken = true
                webViewRef?.stopLoading()
                onTokenExtracted(extractedToken)
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

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
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar"
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "Conectar Conta da Twitch",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        // Botão útil caso o usuário queira limpar a sessão do WebView e logar em outra conta
                        IconButton(
                            onClick = {
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                webViewRef?.clearCache(true)
                                webViewRef?.loadUrl(authUrl)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Limpar sessão e recarregar"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (loadProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF9146FF)
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                webViewRef = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    // Remove a assinatura "; wv" do WebView para evitar bloqueios no login OAuth
                                    userAgentString = userAgentString.replace("; wv", "")
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val targetUrl = request?.url?.toString()
                                        if (tryInterceptToken(targetUrl)) {
                                            // Cancela o carregamento do localhost para não exibir página de erro
                                            return true
                                        }
                                        return false
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        url: String?
                                    ): Boolean {
                                        if (tryInterceptToken(url)) {
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        if (tryInterceptToken(url)) {
                                            view?.stopLoading()
                                            return
                                        }
                                        super.onPageStarted(view, url, favicon)
                                    }
                                }

                                loadUrl(authUrl)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Extrai o `access_token` tanto do fragmento (`#access_token=...`) quanto de query parameter
 * quando a Twitch redireciona de volta para o `redirect_uri` (ex: http://localhost#access_token=xyz&scope=...).
 */
private fun extractAccessTokenFromUrl(url: String, redirectUri: String): String? {
    val startsWithRedirect = url.startsWith(redirectUri, ignoreCase = true)
    val containsTokenMarker = url.contains("#access_token=") || url.contains("&access_token=") || url.contains("?access_token=")

    if (!startsWithRedirect && !containsTokenMarker) return null

    // 1. Tenta extrair do fragmento após o '#' (padrão do Implicit Grant Flow da Twitch)
    val fragment = url.substringAfter("#", missingDelimiterValue = "")
    if (fragment.isNotEmpty()) {
        val params = fragment.split("&")
        for (param in params) {
            val pair = param.split("=", limit = 2)
            if (pair.size == 2 && pair[0] == "access_token") {
                return Uri.decode(pair[1]).trim().takeIf { it.isNotEmpty() }
            }
        }
    }

    // 2. Fallback caso venha na query string
    return runCatching {
        Uri.parse(url).getQueryParameter("access_token")?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}
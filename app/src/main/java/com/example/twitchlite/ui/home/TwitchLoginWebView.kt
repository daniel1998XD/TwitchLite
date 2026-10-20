package com.example.twitchlite.ui.home

import android.annotation.SuppressLint
import android.graphics.Bitmap
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

private const val TWITCH_DIRECT_LOGIN_URL = "https://www.twitch.tv/login"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TwitchLoginWebViewDialog(
    clientId: String = "",
    onTokenExtracted: (accessToken: String) -> Unit,
    onDismiss: () -> Unit
) {
    var loadProgress by remember { mutableIntStateOf(0) }
    var hasExtractedToken by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Verifica se a Twitch já gravou o cookie "auth-token" após o login bem-sucedido
    val checkAndExtractAuthCookie: () -> Boolean = {
        if (!hasExtractedToken) {
            val cookieToken = extractTwitchAuthTokenFromCookies()
            if (!cookieToken.isNullOrBlank()) {
                hasExtractedToken = true
                CookieManager.getInstance().flush()
                webViewRef?.stopLoading()
                onTokenExtracted(cookieToken)
                true
            } else {
                false
            }
        } else {
            true
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
                        // Permite limpar os cookies caso queira trocar de conta da Twitch
                        IconButton(
                            onClick = {
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                                webViewRef?.clearCache(true)
                                webViewRef?.loadUrl(TWITCH_DIRECT_LOGIN_URL)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Limpar sessão e trocar de conta"
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

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    // Usa User-Agent de navegador Chrome Android padrão (sem "; wv")
                                    userAgentString = userAgentString.replace("; wv", "")
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                        if (newProgress >= 80) {
                                            checkAndExtractAuthCookie()
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        if (checkAndExtractAuthCookie()) {
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        if (checkAndExtractAuthCookie()) {
                                            return
                                        }
                                        super.onPageStarted(view, url, favicon)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        checkAndExtractAuthCookie()
                                    }
                                }

                                // Se já houver um cookie antigo salvo e o usuário abriu a tela de login,
                                // captura imediatamente ou carrega a página de login da Twitch
                                if (!checkAndExtractAuthCookie()) {
                                    loadUrl(TWITCH_DIRECT_LOGIN_URL)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Lê os cookies dos domínios da Twitch no CookieManager e extrai o token `auth-token`
 * gerado quando o usuário faz login em `https://www.twitch.tv/login`.
 */
private fun extractTwitchAuthTokenFromCookies(): String? {
    val cookieManager = CookieManager.getInstance()
    val candidateUrls = listOf(
        "https://www.twitch.tv",
        "https://twitch.tv",
        "https://m.twitch.tv",
        "https://id.twitch.tv"
    )

    for (domainUrl in candidateUrls) {
        val rawCookies = cookieManager.getCookie(domainUrl) ?: continue
        val pairs = rawCookies.split(";")
        for (pair in pairs) {
            val parts = pair.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0].trim() == "auth-token") {
                val token = parts[1].trim()
                if (token.isNotBlank() && token != "null") {
                    return token
                }
            }
        }
    }
    return null
}
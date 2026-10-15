package com.example.twitchlite

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.twitchlite.di.AppContainer
import com.example.twitchlite.ui.home.FollowedStreamsScreen
import com.example.twitchlite.ui.home.HomeViewModel
import com.example.twitchlite.ui.player.StreamPlayerScreen
import com.example.twitchlite.ui.player.StreamViewModel

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer
    private var shouldEnterPipOnLeave: Boolean = false
    private var pendingOAuthToken by mutableStateOf<String?>(null)

    fun setPipEligible(eligible: Boolean) {
        shouldEnterPipOnLeave = eligible
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setAutoEnterEnabled(eligible)
                .build()
            setPictureInPictureParams(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (shouldEnterPipOnLeave && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContainer = AppContainer(applicationContext)
        extractOAuthTokenFromIntent(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val homeViewModel: HomeViewModel = viewModel(
                        factory = HomeViewModel.provideFactory(appContainer)
                    )
                    val streamViewModel: StreamViewModel = viewModel(
                        factory = StreamViewModel.provideFactory(appContainer)
                    )

                    var activeChannel by remember { mutableStateOf<String?>(null) }

                    // Se retornou do navegador com #access_token=..., salva e carrega a Helix
                    LaunchedEffect(pendingOAuthToken) {
                        val token = pendingOAuthToken
                        if (!token.isNullOrBlank()) {
                            homeViewModel.authenticateWithToken(
                                accessToken = token,
                                customClientId = AppContainer.TWITCH_CLIENT_ID
                            )
                            pendingOAuthToken = null
                        }
                    }

                    val currentChannel = activeChannel
                    if (currentChannel == null) {
                        FollowedStreamsScreen(
                            viewModel = homeViewModel,
                            onStreamClick = { chosenChannel ->
                                activeChannel = chosenChannel
                            }
                        )
                    } else {
                        StreamPlayerScreen(
                            channelLogin = currentChannel,
                            viewModel = streamViewModel,
                            onBack = {
                                activeChannel = null
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractOAuthTokenFromIntent(intent)
    }

    private fun extractOAuthTokenFromIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "http" && data.host == "localhost") {
            val fragment = data.fragment ?: return
            val tokenParam = fragment.split("&")
                .firstOrNull { it.startsWith("access_token=") }
                ?.substringAfter("access_token=")
            if (!tokenParam.isNullOrBlank()) {
                pendingOAuthToken = tokenParam
            }
        }
    }
}
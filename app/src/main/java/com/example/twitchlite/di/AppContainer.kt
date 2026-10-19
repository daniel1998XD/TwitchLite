package com.example.twitchlite.di

import android.content.Context
import com.example.twitchlite.data.local.TokenManager
import com.example.twitchlite.data.remote.EmoteRepository
import com.example.twitchlite.data.remote.StreamExtractorRepository
import com.example.twitchlite.data.remote.api.ThirdPartyEmoteApi
import com.example.twitchlite.data.remote.api.TwitchAuthRefreshApi
import com.example.twitchlite.data.remote.api.TwitchGqlApi
import com.example.twitchlite.data.remote.api.TwitchHelixApi
import com.example.twitchlite.data.remote.auth.TwitchTokenAuthenticator
import com.example.twitchlite.data.remote.irc.TwitchIrcClient
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    companion object {
        var TWITCH_CLIENT_ID: String = ""
        var TWITCH_CLIENT_SECRET: String = ""
        const val OAUTH_REDIRECT_URI = "http://localhost"
    }

    val tokenManager = TokenManager(context.applicationContext)

    init {
        tokenManager.getClientId()?.let { savedId ->
            if (savedId.isNotBlank()) {
                TWITCH_CLIENT_ID = savedId
            }
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val authRefreshApi: TwitchAuthRefreshApi = Retrofit.Builder()
        .baseUrl("https://id.twitch.tv/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TwitchAuthRefreshApi::class.java)

    private val tokenAuthenticator = TwitchTokenAuthenticator(
        tokenManager = tokenManager,
        authRefreshApi = authRefreshApi,
        clientId = TWITCH_CLIENT_ID,
        clientSecret = TWITCH_CLIENT_SECRET
    )

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .authenticator(tokenAuthenticator)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    val helixApi: TwitchHelixApi = Retrofit.Builder()
        .baseUrl("https://api.twitch.tv/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TwitchHelixApi::class.java)

    val gqlApi: TwitchGqlApi = Retrofit.Builder()
        .baseUrl("https://gql.twitch.tv/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TwitchGqlApi::class.java)

    private val thirdPartyEmoteApi: ThirdPartyEmoteApi = Retrofit.Builder()
        .baseUrl("https://7tv.io/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ThirdPartyEmoteApi::class.java)

    val streamExtractorRepository = StreamExtractorRepository(
        gqlApi = gqlApi,
        tokenManager = tokenManager
    )

    val emoteRepository = EmoteRepository(api = thirdPartyEmoteApi)

    val ircClient = TwitchIrcClient(okHttpClient = okHttpClient)
}
package com.example.twitchlite.data.remote

import com.example.twitchlite.data.remote.api.ThirdPartyEmoteApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class EmoteRepository(
    private val api: ThirdPartyEmoteApi
) {
    private val globalEmotesCache = mutableMapOf<String, String>()

    // Mapa reativo (Código -> URL) contendo Emotes Globais + Emotes do Canal Atual
    private val _activeEmotes = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeEmotes: StateFlow<Map<String, String>> = _activeEmotes.asStateFlow()

    suspend fun loadGlobalEmotesIfNeeded() = withContext(Dispatchers.IO) {
        if (globalEmotesCache.isNotEmpty()) {
            _activeEmotes.value = globalEmotesCache.toMap()
            return@withContext
        }

        val sevenTvDeferred = async { runCatching { api.getSevenTvGlobal() }.getOrNull() }
        val bttvDeferred = async { runCatching { api.getBttvGlobal() }.getOrNull() }
        val ffzDeferred = async { runCatching { api.getFfzGlobal() }.getOrNull() }

        // 1. FFZ Global
        ffzDeferred.await()?.sets?.values?.forEach { set ->
            set.emoticons?.forEach { emote ->
                globalEmotesCache[emote.name] = "https://cdn.frankerfacez.com/emote/${emote.id}/2"
            }
        }

        // 2. BTTV Global
        bttvDeferred.await()?.forEach { emote ->
            globalEmotesCache[emote.code] = "https://cdn.betterttv.net/emote/${emote.id}/2x"
        }

        // 3. 7TV Global
        sevenTvDeferred.await()?.emotes?.forEach { emote ->
            val rawHost = emote.data?.host?.url
            if (!rawHost.isNullOrBlank()) {
                val cleanHost = if (rawHost.startsWith("//")) "https:$rawHost" else rawHost
                globalEmotesCache[emote.name] = "$cleanHost/2x.webp"
            }
        }

        _activeEmotes.value = globalEmotesCache.toMap()
    }

    suspend fun loadChannelEmotes(roomId: String) = withContext(Dispatchers.IO) {
        loadGlobalEmotesIfNeeded()
        val combined = globalEmotesCache.toMutableMap()

        val sevenTvDeferred = async {
            runCatching { api.getSevenTvChannel("https://7tv.io/v3/users/twitch/$roomId") }.getOrNull()
        }
        val bttvDeferred = async {
            runCatching { api.getBttvChannel("https://api.betterttv.net/3/cached/users/twitch/$roomId") }.getOrNull()
        }
        val ffzDeferred = async {
            runCatching { api.getFfzChannel("https://api.frankerfacez.com/v1/room/id/$roomId") }.getOrNull()
        }

        // 1. FFZ do Canal
        ffzDeferred.await()?.sets?.values?.forEach { set ->
            set.emoticons?.forEach { emote ->
                combined[emote.name] = "https://cdn.frankerfacez.com/emote/${emote.id}/2"
            }
        }

        // 2. BTTV do Canal (channel + shared)
        bttvDeferred.await()?.let { bttv ->
            (bttv.channelEmotes.orEmpty() + bttv.sharedEmotes.orEmpty()).forEach { emote ->
                combined[emote.code] = "https://cdn.betterttv.net/emote/${emote.id}/2x"
            }
        }

        // 3. 7TV do Canal (prioridade máxima caso haja colisão de nomes)
        sevenTvDeferred.await()?.emoteSet?.emotes?.forEach { emote ->
            val rawHost = emote.data?.host?.url
            if (!rawHost.isNullOrBlank()) {
                val cleanHost = if (rawHost.startsWith("//")) "https:$rawHost" else rawHost
                combined[emote.name] = "$cleanHost/2x.webp"
            }
        }

        _activeEmotes.value = combined
    }
}
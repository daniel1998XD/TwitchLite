package com.example.twitchlite.data.remote.irc

import com.example.twitchlite.domain.model.ChatMessage
import com.example.twitchlite.domain.model.EmotePosition
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.*
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class TwitchIrcClient(
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null

    private var webSocket: WebSocket? = null
    private var currentChannel: String? = null
    private var currentToken: String? = null
    private var currentUsername: String? = null

    @Volatile
    private var isIntentionalDisconnect = false
    private var retryAttempt = 0

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun connect(channel: String, oauthToken: String?, username: String?) {
        disconnect(clearMessages = true)
        isIntentionalDisconnect = false
        retryAttempt = 0

        currentChannel = channel.lowercase()
        currentToken = oauthToken
        // Caso o usuário não tenha token, permite leitura anônima via justinfan
        currentUsername = username?.lowercase()?.ifBlank { "justinfan12345" } ?: "justinfan12345"

        openSocketConnection()
    }

    private fun openSocketConnection() {
        val channel = currentChannel ?: return
        val username = currentUsername ?: "justinfan12345"
        val oauthToken = currentToken

        val request = Request.Builder()
            .url("wss://irc-ws.chat.twitch.tv:443")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                retryAttempt = 0
                webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                if (!oauthToken.isNullOrBlank() && !username.startsWith("justinfan")) {
                    val cleanToken = oauthToken
                        .removePrefix("oauth:")
                        .removePrefix("OAuth ")
                        .removePrefix("Bearer ")
                        .trim()
                    webSocket.send("PASS oauth:$cleanToken")
                } else {
                    webSocket.send("PASS SCHMOOPIIE")
                }
                webSocket.send("NICK $username")
                webSocket.send("JOIN #$channel")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                text.split("\r\n").filter { it.isNotBlank() }.forEach { line ->
                    handleIrcLine(webSocket, line)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isIntentionalDisconnect) {
                    scheduleReconnectWithBackoff()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isIntentionalDisconnect) {
                    scheduleReconnectWithBackoff()
                }
            }
        })
    }

    private fun scheduleReconnectWithBackoff() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val exponentialDelayMs = (1000.0 * 2.0.pow(retryAttempt.toDouble())).toLong()
            val cappedDelayMs = min(exponentialDelayMs, 60_000L)
            val jitterMs = Random.nextLong(0, 1000)

            retryAttempt++
            delay(cappedDelayMs + jitterMs)

            if (!isIntentionalDisconnect) {
                openSocketConnection()
            }
        }
    }

    private fun handleIrcLine(ws: WebSocket, line: String) {
        if (line.startsWith("PING")) {
            ws.send(line.replace("PING", "PONG"))
            return
        }

        if (line.contains("PRIVMSG")) {
            parsePrivMsg(line)?.let { msg ->
                _messages.update { currentList ->
                    (currentList + msg).takeLast(200)
                }
            }
        }
    }

    private fun parsePrivMsg(rawLine: String): ChatMessage? {
        return try {
            val displayName = Regex("display-name=([^;\\s]+)").find(rawLine)?.groupValues?.get(1)
            val color = Regex("color=(#[0-9A-Fa-f]{6})").find(rawLine)?.groupValues?.get(1) ?: "#9146FF"
            val fallbackUser = Regex(":([^!]+)!").find(rawLine)?.groupValues?.get(1) ?: "Usuário"

            val emotesRaw = Regex("(?:^|;)emotes=([^;\\s]*)").find(rawLine)?.groupValues?.get(1).orEmpty()
            val parsedEmotes = parseEmotesTag(emotesRaw)

            val channelMarker = "PRIVMSG #$currentChannel :"
            val messageIndex = rawLine.indexOf(channelMarker)
            if (messageIndex == -1) return null

            val content = rawLine.substring(messageIndex + channelMarker.length)

            ChatMessage(
                username = displayName?.ifBlank { fallbackUser } ?: fallbackUser,
                content = content,
                colorHex = color,
                emotes = parsedEmotes
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseEmotesTag(emotesTag: String): List<EmotePosition> {
        if (emotesTag.isBlank()) return emptyList()
        val positions = mutableListOf<EmotePosition>()

        emotesTag.split("/").forEach { emoteEntry ->
            val parts = emoteEntry.split(":")
            if (parts.size == 2) {
                val emoteId = parts[0]
                val ranges = parts[1].split(",")
                ranges.forEach { range ->
                    val bounds = range.split("-")
                    if (bounds.size == 2) {
                        val start = bounds[0].toIntOrNull()
                        val end = bounds[1].toIntOrNull()
                        if (start != null && end != null) {
                            positions.add(EmotePosition(emoteId, start, end))
                        }
                    }
                }
            }
        }
        return positions.sortedBy { it.startIndex }
    }

    fun sendMessage(text: String) {
        val channel = currentChannel ?: return
        val user = currentUsername ?: return
        if (text.isBlank() || user.startsWith("justinfan")) return

        webSocket?.send("PRIVMSG #$channel :$text")
        _messages.update { currentList ->
            (currentList + ChatMessage(username = user, content = text)).takeLast(200)
        }
    }

    fun disconnect(clearMessages: Boolean = true) {
        isIntentionalDisconnect = true
        reconnectJob?.cancel()
        webSocket?.close(1000, "Saindo do canal")
        webSocket = null
        if (clearMessages) {
            _messages.value = emptyList()
        }
    }
}
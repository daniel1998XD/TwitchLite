package com.example.twitchlite.ui.player

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.twitchlite.domain.model.ChatMessage

@Composable
fun rememberAnimatedEmoteImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    thirdPartyEmotes: Map<String, String>,
    imageLoader: ImageLoader
) {
    val userColor = remember(message.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(message.colorHex)) }
            .getOrDefault(Color(0xFF9146FF))
    }

    val (annotatedString, inlineContentMap) = remember(message, thirdPartyEmotes) {
        buildMessageWithAllEmotes(
            message = message,
            userColor = userColor,
            thirdPartyEmotes = thirdPartyEmotes,
            imageLoader = imageLoader
        )
    }

    Text(
        text = annotatedString,
        inlineContent = inlineContentMap,
        style = MaterialTheme.typography.bodyMedium
    )
}

private fun buildMessageWithAllEmotes(
    message: ChatMessage,
    userColor: Color,
    thirdPartyEmotes: Map<String, String>,
    imageLoader: ImageLoader
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val text = message.content

    fun registerInlineEmote(inlineId: String, code: String, url: String) {
        if (!inlineContent.containsKey(inlineId)) {
            inlineContent[inlineId] = InlineTextContent(
                placeholder = Placeholder(
                    width = 22.sp,
                    height = 22.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                AsyncImage(
                    model = url,
                    imageLoader = imageLoader,
                    contentDescription = code,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    /**
     * Analisa um trecho de texto sem emotes nativos procurando códigos da 7TV, BTTV ou FFZ.
     */
    fun AnnotatedString.Builder.appendTextSegmentWithThirdPartyEmotes(segment: String) {
        if (segment.isEmpty()) return
        if (thirdPartyEmotes.isEmpty()) {
            append(segment)
            return
        }

        val tokens = segment.split(" ")
        tokens.forEachIndexed { index, word ->
            val emoteUrl = thirdPartyEmotes[word]
            if (emoteUrl != null) {
                val inlineId = "3p_$word"
                appendInlineContent(id = inlineId, alternateText = word)
                registerInlineEmote(inlineId, word, emoteUrl)
            } else {
                append(word)
            }
            if (index < tokens.lastIndex) {
                append(" ")
            }
        }
    }

    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = userColor, fontWeight = FontWeight.Bold)) {
            append("${message.username}: ")
        }

        withStyle(SpanStyle(color = Color.White)) {
            if (message.emotes.isEmpty()) {
                appendTextSegmentWithThirdPartyEmotes(text)
            } else {
                var currentIndex = 0
                for (emote in message.emotes) {
                    val safeStart = emote.startIndex.coerceAtLeast(0)
                    val safeEnd = (emote.endIndex + 1).coerceAtMost(text.length)

                    if (safeStart > currentIndex && safeStart <= text.length) {
                        val plainSegment = text.substring(currentIndex, safeStart)
                        appendTextSegmentWithThirdPartyEmotes(plainSegment)
                    }

                    if (safeStart < safeEnd) {
                        val emoteCode = text.substring(safeStart, safeEnd)
                        val inlineId = "twitch_${emote.emoteId}"
                        val emoteUrl =
                            "https://static-cdn.jtvnw.net/emoticons/v2/${emote.emoteId}/default/dark/2.0"

                        appendInlineContent(id = inlineId, alternateText = emoteCode)
                        registerInlineEmote(inlineId, emoteCode, emoteUrl)
                        currentIndex = safeEnd
                    }
                }
                if (currentIndex < text.length) {
                    val remainingSegment = text.substring(currentIndex)
                    appendTextSegmentWithThirdPartyEmotes(remainingSegment)
                }
            }
        }
    }

    return annotated to inlineContent
}
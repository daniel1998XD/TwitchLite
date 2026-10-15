package com.example.twitchlite.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.twitchlite.domain.model.ChatMessage

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val userColor = remember(message.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(message.colorHex)) }
            .getOrDefault(Color(0xFF9146FF))
    }

    val (annotatedString, inlineContentMap) = remember(message) {
        buildMessageWithEmotes(message, userColor)
    }

    Text(
        text = annotatedString,
        inlineContent = inlineContentMap,
        style = MaterialTheme.typography.bodyMedium
    )
}

private fun buildMessageWithEmotes(
    message: ChatMessage,
    userColor: Color
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val text = message.content

    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = userColor, fontWeight = FontWeight.Bold)) {
            append("${message.username}: ")
        }

        withStyle(SpanStyle(color = Color.White)) {
            if (message.emotes.isEmpty()) {
                append(text)
            } else {
                var currentIndex = 0
                for (emote in message.emotes) {
                    val safeStart = emote.startIndex.coerceAtLeast(0)
                    val safeEnd = (emote.endIndex + 1).coerceAtMost(text.length)

                    if (safeStart > currentIndex && safeStart <= text.length) {
                        append(text.substring(currentIndex, safeStart))
                    }

                    if (safeStart < safeEnd) {
                        val emoteCode = text.substring(safeStart, safeEnd)
                        val inlineId = "emote_${emote.emoteId}"
                        appendInlineContent(id = inlineId, alternateText = emoteCode)

                        if (!inlineContent.containsKey(inlineId)) {
                            val emoteUrl =
                                "https://static-cdn.jtvnw.net/emoticons/v2/${emote.emoteId}/default/dark/2.0"
                            inlineContent[inlineId] = InlineTextContent(
                                placeholder = Placeholder(
                                    width = 20.sp,
                                    height = 20.sp,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                                )
                            ) {
                                AsyncImage(
                                    model = emoteUrl,
                                    contentDescription = emoteCode,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        currentIndex = safeEnd
                    }
                }
                if (currentIndex < text.length) {
                    append(text.substring(currentIndex))
                }
            }
        }
    }

    return annotated to inlineContent
}
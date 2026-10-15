package com.example.twitchlite.domain.model

import java.util.UUID

data class EmotePosition(
    val emoteId: String,
    val startIndex: Int,
    val endIndex: Int
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val content: String,
    val colorHex: String = "#9146FF",
    val emotes: List<EmotePosition> = emptyList()
)
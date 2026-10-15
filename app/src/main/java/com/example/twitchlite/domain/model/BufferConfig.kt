package com.example.twitchlite.domain.model

/**
 * Parâmetros de controle de buffer para o DefaultLoadControl do Media3/ExoPlayer.
 */
data class BufferConfig(
    val minBufferMs: Int = 15_000,                   // Mantém no mínimo 15s de vídeo em buffer
    val maxBufferMs: Int = 45_000,                   // Limite máximo de 45s em memória
    val bufferForPlaybackMs: Int = 2_500,            // Inicia a reprodução após carregar 2.5s
    val bufferForPlaybackAfterRebufferMs: Int = 5_000 // Exige 5s após queda de rede (rebuffer)
)
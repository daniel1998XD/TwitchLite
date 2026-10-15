package com.example.twitchlite.domain.model

import androidx.media3.common.TrackGroup

/**
 * Representa uma resolução extraída do Master Playlist (.m3u8) da Usher.
 */
data class VideoQualityOption(
    val label: String,           // Ex: "1080p60 (6.0 Mbps)"
    val height: Int,
    val frameRate: Int,
    val bitrate: Int,
    val trackGroup: TrackGroup,
    val trackIndex: Int
)
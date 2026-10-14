package com.metrolist.desktop.model

import kotlinx.serialization.Serializable

@Serializable
data class DesktopSong(
    val id: String,
    val title: String,
    val artist: String,
    val durationText: String,
    val thumbnailUrl: String? = null,
    val album: String? = null,
)

@Serializable
data class DesktopPlaylistSummary(
    val id: String,
    val title: String,
    val author: String? = null,
    val songCountText: String? = null,
    val thumbnailUrl: String? = null,
    val isLocal: Boolean = false,
    val description: String? = null,
)

@Serializable
data class DesktopPlaylist(
    val id: String,
    val title: String,
    val author: String = "Lokal",
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val songs: List<DesktopSong> = emptyList(),
    val isLocal: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toSummary(): DesktopPlaylistSummary = DesktopPlaylistSummary(
        id = id,
        title = title,
        author = author,
        songCountText = "${songs.size} lagu",
        thumbnailUrl = thumbnailUrl ?: songs.firstOrNull()?.thumbnailUrl,
        isLocal = isLocal,
        description = description,
    )
}

@Serializable
data class NowPlayingArtistInfo(
    val artistName: String,
    val bannerUrl: String? = null,
    val monthlyListenersText: String? = null,
    val bio: String? = null,
)

@Serializable
data class NowPlayingCreditItem(
    val name: String,
    val role: String,
    val canFollow: Boolean = false,
)

@Serializable
data class NowPlayingTourItem(
    val month: String,
    val day: String,
    val city: String,
    val lineUp: String,
    val venueText: String,
)

private val GOOGLEUSERCONTENT_SIZE_PATTERN =
    Regex("^(https://(?:lh3|yt3)\\.googleusercontent\\.com/[^?]*?)=w(\\d+)-h(\\d+)[^?]*(\\?.*)?$")
private val GGPHT_SIZE_PATTERN =
    Regex("^(https://yt3\\.ggpht\\.com/[^?=]+)=(?:s\\d+|w\\d+-h\\d+)[^?]*(\\?.*)?$")

fun String.resize(width: Int? = null, height: Int? = null): String {
    if (width == null && height == null) return this
    GOOGLEUSERCONTENT_SIZE_PATTERN.matchEntire(this)?.groupValues?.let { group ->
        val originalWidth = group[2].toInt()
        val originalHeight = group[3].toInt()
        val query = group[4]
        val targetWidth = width ?: ((height!!.toDouble() * originalWidth) / originalHeight).toInt()
        val targetHeight = height ?: ((width!!.toDouble() * originalHeight) / originalWidth).toInt()
        return "${group[1]}=w${targetWidth.coerceAtLeast(1)}-h${targetHeight.coerceAtLeast(1)}-p-l90-rj$query"
    }
    GGPHT_SIZE_PATTERN.matchEntire(this)?.groupValues?.let { group ->
        val query = group[2]
        return if (width != null && height != null) {
            "${group[1]}=w$width-h$height-p-l90-rj$query"
        } else {
            "${group[1]}=s${width ?: height}$query"
        }
    }
    return this
}

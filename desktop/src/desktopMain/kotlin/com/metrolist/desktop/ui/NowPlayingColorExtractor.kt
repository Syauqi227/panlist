package com.metrolist.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.metrolist.desktop.model.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import javax.imageio.ImageIO
import kotlin.math.absoluteValue

object NowPlayingColorExtractor {
    private val colorCache = mutableMapOf<String, Color>()

    // Hanya menyimpan 1 lagu aktif di memori (~1 MB), lagu lama langsung dibebaskan
    @Volatile
    private var activeSongId: String? = null
    @Volatile
    private var activeBitmap: ImageBitmap? = null

    val defaultPalettes = listOf(
        Color(0xFF0A4B52), // Deep Teal / Cyan (exact shade in Spotify screenshot)
        Color(0xFF133B5C), // Deep Indigo Blue
        Color(0xFF4A1A2E), // Deep Ruby / Velvet
        Color(0xFF13452C), // Forest Emerald
        Color(0xFF38234B), // Royal Violet
        Color(0xFF523315), // Deep Warm Amber
        Color(0xFF193240), // Dark Ocean
        Color(0xFF3C1F37), // Dark Plum
    )

    fun getAtmosphericColor(songId: String): Color {
        colorCache[songId]?.let { return it }
        val idx = (songId.hashCode().absoluteValue) % defaultPalettes.size
        return defaultPalettes[idx]
    }

    fun getCachedBitmap(songId: String): ImageBitmap? {
        return if (songId == activeSongId) activeBitmap else null
    }

    suspend fun extractOrSampleColor(songId: String, thumbnailUrl: String?): Color {
        colorCache[songId]?.let { return it }
        val fallback = defaultPalettes[(songId.hashCode().absoluteValue) % defaultPalettes.size]
        if (thumbnailUrl.isNullOrBlank()) {
            colorCache[songId] = fallback
            return fallback
        }

        try {
            // Gunakan ukuran 500x500 agar efisien dalam decoding namun tetap tajam untuk NowPlaying cover
            val targetUrl = thumbnailUrl.resize(500, 500)
            val image = withContext(Dispatchers.IO) {
                val url = URI(targetUrl).toURL()
                val connection = url.openConnection()
                connection.connectTimeout = 2500
                connection.readTimeout = 2500
                val stream = connection.getInputStream()
                val img = ImageIO.read(stream)
                stream.close()
                img
            }

            if (image != null) {
                // Simpan hanya untuk lagu aktif saat ini (lagu lama otomatis dibuang dari RAM)
                val composeBitmap = image.toComposeImageBitmap()
                activeSongId = songId
                activeBitmap = composeBitmap

                // Process heavy pixel analysis on Dispatchers.Default
                val sampledColor = withContext(Dispatchers.Default) {
                    var rTotal = 0L
                    var gTotal = 0L
                    var bTotal = 0L
                    var count = 0
                    val stepX = (image.width / 10).coerceAtLeast(1)
                    val stepY = (image.height / 10).coerceAtLeast(1)

                    for (x in 0 until image.width step stepX) {
                        for (y in 0 until image.height step stepY) {
                            val rgb = image.getRGB(x, y)
                            val r = (rgb shr 16) and 0xFF
                            val g = (rgb shr 8) and 0xFF
                            val b = rgb and 0xFF
                            val brightness = (r + g + b) / 3
                            if (brightness in 35..215) {
                                rTotal += r
                                gTotal += g
                                bTotal += b
                                count++
                            }
                        }
                    }

                    if (count > 0) {
                        val avgR = ((rTotal / count) * 0.70f).toInt().coerceIn(10, 110)
                        val avgG = ((gTotal / count) * 0.70f).toInt().coerceIn(10, 110)
                        val avgB = ((bTotal / count) * 0.70f).toInt().coerceIn(10, 110)
                        Color(avgR, avgG, avgB)
                    } else null
                }

                if (sampledColor != null) {
                    colorCache[songId] = sampledColor
                    return sampledColor
                }
            }
        } catch (_: Exception) {}

        colorCache[songId] = fallback
        return fallback
    }
}

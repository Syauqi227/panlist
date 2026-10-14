package com.metrolist.desktop.smtc

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.metrolist.desktop.player.VlcAudioPlayer
import com.metrolist.desktop.player.DesktopPlaybackManager

class WindowsMediaControls(
    private val player: VlcAudioPlayer,
    private val playbackManager: DesktopPlaybackManager? = null,
) {
    /**
     * Handles keyboard events sent to the Compose Desktop window.
     * Returns true if the key event was consumed.
     */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false

        return when {
            // Media keys (standard Windows multimedia keyboard keys)
            event.key == Key.MediaPlayPause -> {
                player.togglePlayPause()
                true
            }
            event.key == Key.MediaStop -> {
                player.pause()
                true
            }
            event.key == Key.MediaNext || event.key == Key.MediaSkipForward -> {
                playbackManager?.playNext()
                true
            }
            event.key == Key.MediaPrevious || event.key == Key.MediaSkipBackward -> {
                playbackManager?.playPrevious()
                true
            }

            // Spacebar: Play / Pause (if not typing in a text field)
            event.key == Key.Spacebar && !event.isCtrlPressed -> {
                player.togglePlayPause()
                true
            }

            // Ctrl + Arrow Right / Left: Seek 5s
            // Ctrl + Alt + Arrow Right / Left: Next / Prev Track in queue
            event.isCtrlPressed && event.isAltPressed && event.key == Key.DirectionRight -> {
                playbackManager?.playNext()
                true
            }
            event.isCtrlPressed && event.isAltPressed && event.key == Key.DirectionLeft -> {
                playbackManager?.playPrevious()
                true
            }

            event.isCtrlPressed && event.key == Key.DirectionRight -> {
                val newPos = player.currentPosition.value + 5000L
                player.seekTo(newPos.coerceAtMost(player.duration.value))
                true
            }
            event.isCtrlPressed && event.key == Key.DirectionLeft -> {
                val newPos = (player.currentPosition.value - 5000L).coerceAtLeast(0L)
                player.seekTo(newPos)
                true
            }

            // Ctrl + Arrow Up / Down: Volume adjustment
            event.isCtrlPressed && event.key == Key.DirectionUp -> {
                player.setVolume(player.volume.value + 5)
                true
            }
            event.isCtrlPressed && event.key == Key.DirectionDown -> {
                player.setVolume(player.volume.value - 5)
                true
            }

            else -> false
        }
    }
}

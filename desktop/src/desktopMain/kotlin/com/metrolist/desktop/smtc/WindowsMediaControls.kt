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
                playbackManager?.togglePlayPause() ?: player.togglePlayPause()
                true
            }
            event.key == Key.MediaStop -> {
                playbackManager?.pause() ?: player.pause()
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
                playbackManager?.togglePlayPause() ?: player.togglePlayPause()
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
                val clamped = newPos.coerceAtMost(player.duration.value)
                playbackManager?.seekTo(clamped) ?: player.seekTo(clamped)
                true
            }
            event.isCtrlPressed && event.key == Key.DirectionLeft -> {
                val newPos = (player.currentPosition.value - 5000L).coerceAtLeast(0L)
                playbackManager?.seekTo(newPos) ?: player.seekTo(newPos)
                true
            }

            // Ctrl + Arrow Up / Down: Volume adjustment
            event.isCtrlPressed && event.key == Key.DirectionUp -> {
                val newVol = (player.volume.value + 5).coerceIn(0, 100)
                playbackManager?.setVolume(newVol) ?: player.setVolume(newVol)
                true
            }
            event.isCtrlPressed && event.key == Key.DirectionDown -> {
                val newVol = (player.volume.value - 5).coerceIn(0, 100)
                playbackManager?.setVolume(newVol) ?: player.setVolume(newVol)
                true
            }

            else -> false
        }
    }
}

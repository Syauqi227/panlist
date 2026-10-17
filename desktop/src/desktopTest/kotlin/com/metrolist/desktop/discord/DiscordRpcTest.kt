package com.metrolist.desktop.discord

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DiscordRpcTest {

    @Test
    fun testDefaultClientIdMatchesRegisteredEchoApp() {
        assertEquals("1560875581988671528", DiscordIpcClient.DEFAULT_CLIENT_ID)
    }

    @Test
    fun testPayloadStructureForListeningState() {
        val now = System.currentTimeMillis()
        val durationMs = 210000L // 3m 30s
        val positionMs = 30000L // 30s
        val start = now - positionMs
        val end = start + durationMs

        val payload = DiscordActivityPayload(
            type = 2,
            details = "Starboy",
            state = "The Weeknd, Daft Punk",
            startTimestamp = start,
            endTimestamp = end,
            largeImage = "https://lh3.googleusercontent.com/test",
            largeText = "Starboy Album",
            smallImage = DesktopDiscordRpc.DEFAULT_ICON_URL,
            smallText = "Echo Desktop",
            buttons = listOf(
                "Dengarkan di YouTube Music" to "https://music.youtube.com/watch?v=d_HlPboLRL8"
            )
        )

        assertEquals(2, payload.type)
        assertEquals("Starboy", payload.details)
        assertEquals("The Weeknd, Daft Punk", payload.state)
        assertEquals(start, payload.startTimestamp)
        assertEquals(end, payload.endTimestamp)
        assertEquals(1, payload.buttons.size)
        assertEquals("Dengarkan di YouTube Music", payload.buttons[0].first)
    }

    @Test
    fun testPausedStateOmitsEndTimestamp() {
        val payload = DiscordActivityPayload(
            type = 2,
            details = "Starboy",
            state = "The Weeknd (Dijeda)",
            startTimestamp = null,
            endTimestamp = null,
            smallText = "Dijeda"
        )

        assertNull("Paused state must not have endTimestamp", payload.endTimestamp)
        assertNull("Paused state must not have startTimestamp", payload.startTimestamp)
        assertEquals("Dijeda", payload.smallText)
    }

    @Test
    fun testDurationParsingFallback() {
        assertEquals(186000L, DesktopDiscordRpc.parseDurationText("3:06"))
        assertEquals(258000L, DesktopDiscordRpc.parseDurationText("04:18"))
        assertEquals(3858000L, DesktopDiscordRpc.parseDurationText("1:04:18"))
        assertEquals(0L, DesktopDiscordRpc.parseDurationText(""))
        assertEquals(0L, DesktopDiscordRpc.parseDurationText(null))
        assertEquals(0L, DesktopDiscordRpc.parseDurationText("--:--"))
    }

    @Test
    fun testLiveIpcClientLifecycle() = runBlocking {
        val client = DiscordIpcClient()
        var isConnected = false
        client.onStatusChanged = { connected ->
            isConnected = connected
        }

        client.start()
        // Tunggu koneksi terbentuk jika Discord sedang berjalan
        delay(1200)

        // Set activity pertama
        client.setActivity(
            DiscordActivityPayload(
                type = 2,
                details = "Lagu Uji Satu",
                state = "by Artis Uji",
                largeImage = DesktopDiscordRpc.DEFAULT_ICON_URL,
                largeText = "Album Uji",
            )
        )
        delay(1000)

        // Set activity kedua (simulasi lagu berganti secara real-time)
        client.setActivity(
            DiscordActivityPayload(
                type = 2,
                details = "Lagu Uji Dua",
                state = "by Artis Dua",
                largeImage = DesktopDiscordRpc.DEFAULT_ICON_URL,
                largeText = "Album Dua",
            )
        )
        delay(1000)

        // Clear activity (simulasi toggle OFF)
        client.clearActivity()
        delay(500)

        client.stop()
        delay(300)
    }
}

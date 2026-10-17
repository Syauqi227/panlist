package com.metrolist.desktop.cast

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsCastHelperTest {

    @Test
    fun testIsWindowsQueryDoesNotThrow() {
        val result = runCatching { WindowsCastHelper.isWindows() }
        assertTrue("isWindows() check must never throw an exception", result.isSuccess)
        assertNotNull(result.getOrNull())
    }

    @Test
    fun testOpenUriInvalidSchemeDoesNotThrow() {
        val result = runCatching { WindowsCastHelper.openUri("invalid:::protocol:::test") }
        assertTrue("openUri() must handle invalid URIs gracefully", result.isSuccess)
    }

    @Test
    fun testOpenCastFlyoutDoesNotThrow() {
        // Pemanggilan method tidak boleh melempar unhandled exception pada JVM runner
        val result = runCatching {
            // Kita tidak perlu mengeksekusi keyboard event pada headless CI jika bukan windows
            if (!WindowsCastHelper.isWindows()) {
                WindowsCastHelper.openCastFlyout()
            }
        }
        assertTrue("openCastFlyout() must not throw unhandled exception", result.isSuccess)
    }

    @Test
    fun testOpenBluetoothSettingsDoesNotThrow() {
        val result = runCatching {
            if (!WindowsCastHelper.isWindows()) {
                WindowsCastHelper.openBluetoothSettings()
            }
        }
        assertTrue("openBluetoothSettings() must not throw unhandled exception", result.isSuccess)
    }
}

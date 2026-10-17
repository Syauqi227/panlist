package com.metrolist.desktop.cast

import com.sun.jna.Library
import com.sun.jna.Native
import java.awt.Desktop
import java.net.URI

internal interface User32Ext : Library {
    fun keybd_event(bVk: Byte, bScan: Byte, dwFlags: Int, dwExtraInfo: Long)

    companion object {
        val INSTANCE: User32Ext by lazy {
            Native.load("user32", User32Ext::class.java)
        }
    }
}

object WindowsCastHelper {
    private const val VK_LWIN: Byte = 0x5B
    private const val VK_K: Byte = 0x4B
    private const val KEYEVENTF_KEYUP: Int = 0x0002

    /**
     * Membuka panel Cast nirkabel (Wireless Display) Windows 10/11 secara native
     * melalui simulasi tombol keyboard Win + K.
     * Jika terjadi kegagalan atau platform bukan Windows, fallback ke URI Settings Windows.
     */
    fun openCastFlyout(): Boolean {
        return try {
            if (isWindows()) {
                val user32 = User32Ext.INSTANCE
                // Tekan tombol Win + K
                user32.keybd_event(VK_LWIN, 0, 0, 0L)
                user32.keybd_event(VK_K, 0, 0, 0L)
                // Lepaskan tombol K + Win
                user32.keybd_event(VK_K, 0, KEYEVENTF_KEYUP, 0L)
                user32.keybd_event(VK_LWIN, 0, KEYEVENTF_KEYUP, 0L)
                true
            } else {
                openUri("ms-settings-connectabledevices:devicediscovery")
            }
        } catch (_: Throwable) {
            openUri("ms-settings-connectabledevices:devicediscovery")
        }
    }

    /**
     * Membuka halaman pengaturan Bluetooth & Perangkat Lainnya di Windows Settings.
     */
    fun openBluetoothSettings(): Boolean {
        return openUri("ms-settings:bluetooth")
    }

    /**
     * Membuka URI sistem menggunakan Desktop AWT atau fallback ke cmd start.
     */
    fun openUri(uriString: String): Boolean {
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(uriString))
                true
            } else {
                if (isWindows()) {
                    ProcessBuilder("cmd", "/c", "start", uriString).start()
                    true
                } else {
                    false
                }
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun isWindows(): Boolean =
        System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true
}

package com.metrolist.desktop.memory

import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object DesktopMemoryManager {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var idleTrimJob: Job? = null

    private interface Kernel32Api : StdCallLibrary {
        fun GetCurrentProcess(): Pointer
        fun SetProcessWorkingSetSize(
            hProcess: Pointer,
            dwMinimumWorkingSetSize: Long,
            dwMaximumWorkingSetSize: Long
        ): Boolean

        companion object {
            val INSTANCE: Kernel32Api? = try {
                Native.load("kernel32", Kernel32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private interface PsapiApi : StdCallLibrary {
        fun EmptyWorkingSet(hProcess: Pointer): Boolean

        companion object {
            val INSTANCE: PsapiApi? = try {
                Native.load("psapi", PsapiApi::class.java, W32APIOptions.DEFAULT_OPTIONS)
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun onWindowStateChanged(isMinimized: Boolean) {
        if (isMinimized) {
            idleTrimJob?.cancel()
            performMemoryTrim(isDeep = true)
        }
    }

    fun onWindowFocusChanged(isFocused: Boolean) {
        if (!isFocused) {
            idleTrimJob?.cancel()
            idleTrimJob = scope.launch {
                // Jeda 30 detik saat jendela tidak fokus di background (misal sedang bermain game)
                delay(30_000L)
                performMemoryTrim(isDeep = true)
            }
        } else {
            idleTrimJob?.cancel()
        }
    }

    fun performMemoryTrim(isDeep: Boolean = false) {
        try {
            // 1. Bersihkan memory cache Coil (Disk Cache lokal tetap utuh dan siap pakai)
            try {
                SingletonImageLoader.get(PlatformContext.INSTANCE).memoryCache?.clear()
            } catch (_: Throwable) {
            }

            // 2. Beri sinyal GC untuk merapikan heap yang terlepas
            System.gc()

            // 3. Perintahkan Windows Kernel untuk memangkas Working Set (melepas halaman RAM fisik pasif)
            if (System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true) {
                val kernel = Kernel32Api.INSTANCE
                if (kernel != null) {
                    val process = kernel.GetCurrentProcess()
                    kernel.SetProcessWorkingSetSize(process, -1L, -1L)
                }
                val psapi = PsapiApi.INSTANCE
                if (psapi != null) {
                    val kernelInstance = Kernel32Api.INSTANCE
                    val process = kernelInstance?.GetCurrentProcess()
                    if (process != null) {
                        psapi.EmptyWorkingSet(process)
                    }
                }
            }
        } catch (_: Throwable) {
            // Graceful fallback
        }
    }
}

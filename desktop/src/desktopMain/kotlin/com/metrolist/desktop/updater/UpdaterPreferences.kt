package com.metrolist.desktop.updater

import java.io.File
import java.util.Properties

open class UpdaterPreferences(private val customFile: File? = null) {
    private val lock = Any()

    private val configFile: File by lazy {
        if (customFile != null) return@lazy customFile
        val baseDir = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")?.takeIf { it.isNotBlank() }
            ?: "."
        val echoDir = File(baseDir, "Echo")
        val metrolistDir = File(baseDir, "Metrolist")
        val targetDir = if (echoDir.exists()) echoDir else if (metrolistDir.exists()) metrolistDir else echoDir
        targetDir.apply { mkdirs() }.resolve("updater.properties")
    }

    private val props: Properties by lazy {
        Properties().apply {
            try {
                if (configFile.exists()) {
                    configFile.inputStream().use { load(it) }
                }
            } catch (_: Exception) {}
        }
    }

    private fun save() {
        try {
            configFile.parentFile?.mkdirs()
            configFile.outputStream().use { props.store(it, "Echo Desktop Updater Preferences") }
        } catch (_: Exception) {}
    }

    var dismissedTag: String
        get() = synchronized(lock) { props.getProperty(KEY_DISMISSED_TAG, "") }
        set(value) = synchronized(lock) {
            props.setProperty(KEY_DISMISSED_TAG, value)
            save()
        }

    var autoCheck: Boolean
        get() = synchronized(lock) { props.getProperty(KEY_AUTO_CHECK, "true").toBooleanStrictOrNull() ?: true }
        set(value) = synchronized(lock) {
            props.setProperty(KEY_AUTO_CHECK, value.toString())
            save()
        }

    var notifications: Boolean
        get() = synchronized(lock) { props.getProperty(KEY_NOTIFICATIONS, "true").toBooleanStrictOrNull() ?: true }
        set(value) = synchronized(lock) {
            props.setProperty(KEY_NOTIFICATIONS, value.toString())
            save()
        }

    companion object : UpdaterPreferences() {
        private const val KEY_DISMISSED_TAG = "dismissed_tag"
        private const val KEY_AUTO_CHECK = "auto_check"
        private const val KEY_NOTIFICATIONS = "notifications"
    }
}

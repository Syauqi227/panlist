package com.metrolist.desktop.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.system.exitProcess

data class DesktopReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val description: String,
    val releaseUrl: String,
    val downloadUrl: String?,
    val publishedAt: String,
)

object DesktopUpdater {
    const val CURRENT_VERSION = "13.7.5"
    const val GITHUB_REPO_OWNER = "Syauqi227"
    const val GITHUB_REPO_NAME = "panlist"
    const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    var cachedReleaseInfo: DesktopReleaseInfo? = null
        private set
    var lastCheckTime: Long = -1L
        private set

    private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L // 1 hour

    /**
     * Compares two semantic version strings.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = v1.removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }
        val v2Parts = v2.removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(v1Parts.size, v2Parts.size)

        for (i in 0 until maxLen) {
            val p1 = v1Parts.getOrNull(i) ?: 0
            val p2 = v2Parts.getOrNull(i) ?: 0
            if (p1 > p2) return 1
            if (p1 < p2) return -1
        }
        return 0
    }

    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        return compareVersions(latestVersion, currentVersion) > 0
    }

    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<DesktopReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                if (!forceRefresh && cachedReleaseInfo != null && (now - lastCheckTime) < CHECK_INTERVAL_MS) {
                    val hasUpdate = isUpdateAvailable(CURRENT_VERSION, cachedReleaseInfo!!.versionName)
                    return@runCatching if (hasUpdate) cachedReleaseInfo else null
                }

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(LATEST_RELEASE_API_URL))
                    .header("User-Agent", "Echo-Desktop/$CURRENT_VERSION")
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() == 404) {
                    // No release published yet on this repository
                    return@runCatching null
                }

                if (response.statusCode() !in 200..299) {
                    throw RuntimeException("GitHub API HTTP ${response.statusCode()}")
                }

                val root = json.parseToJsonElement(response.body()).jsonObject
                val tagName = root["tag_name"]?.jsonPrimitive?.content ?: return@runCatching null
                val versionName = tagName.removePrefix("v")
                val title = root["name"]?.jsonPrimitive?.content ?: tagName
                val description = root["body"]?.jsonPrimitive?.content ?: ""
                val releaseUrl = root["html_url"]?.jsonPrimitive?.content ?: "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases"
                val publishedAt = root["published_at"]?.jsonPrimitive?.content ?: ""

                // Find Windows installer asset (.msi, .exe, or .zip)
                val assets = root["assets"]?.jsonArray ?: emptyList()
                var downloadUrl: String? = null
                for (assetElement in assets) {
                    val asset = assetElement.jsonObject
                    val name = asset["name"]?.jsonPrimitive?.content?.lowercase() ?: ""
                    val url = asset["browser_download_url"]?.jsonPrimitive?.content
                    if (name.endsWith(".msi") || name.endsWith(".exe") || name.endsWith(".zip")) {
                        downloadUrl = url
                        break
                    }
                }
                if (downloadUrl == null) {
                    downloadUrl = releaseUrl
                }

                val releaseInfo = DesktopReleaseInfo(
                    tagName = tagName,
                    versionName = versionName,
                    title = title,
                    description = description,
                    releaseUrl = releaseUrl,
                    downloadUrl = downloadUrl,
                    publishedAt = publishedAt,
                )

                cachedReleaseInfo = releaseInfo
                lastCheckTime = now

                if (isUpdateAvailable(CURRENT_VERSION, versionName)) {
                    releaseInfo
                } else {
                    null
                }
            }
        }

    fun openBrowser(url: String) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url))
            } else {
                Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", url.replace("&", "^&")))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showDesktopNotification(title: String, message: String) {
        try {
            if (SystemTray.isSupported()) {
                val tray = SystemTray.getSystemTray()
                val image = Toolkit.getDefaultToolkit().createImage(
                    DesktopUpdater::class.java.getResource("/icons/icon.png")
                )
                val trayIcon = TrayIcon(image, "Echo")
                trayIcon.isImageAutoSize = true
                tray.add(trayIcon)
                trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO)
            }
        } catch (_: Throwable) {
            // Silently fallback if system tray is not available
        }
    }

    suspend fun downloadAndInstallUpdate(
        downloadUrl: String,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("User-Agent", "Echo-Desktop/$CURRENT_VERSION")
                .timeout(Duration.ofMinutes(15))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() !in 200..299) {
                throw RuntimeException("Gagal mengunduh installer (HTTP ${response.statusCode()})")
            }

            val contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
            val isMsi = downloadUrl.substringBefore("?").endsWith(".msi", ignoreCase = true)
            val suffix = if (isMsi) ".msi" else ".exe"
            val tempFile = File.createTempFile("echo_update_", suffix)

            response.body().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            onProgress((totalRead.toDouble() / contentLength.toDouble()).toFloat().coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }

            installAndRestart(tempFile)
        }
    }

    fun installAndRestart(installerFile: File) {
        val appPath = ProcessHandle.current().info().command().orElse("")
        val isMsi = installerFile.name.endsWith(".msi", ignoreCase = true)
        val tempDir = System.getProperty("java.io.tmpdir")
        val batFile = File(tempDir, "echo_updater_${System.currentTimeMillis()}.bat")

        val installCmd = if (isMsi) {
            "start /wait msiexec /i \"%~1\" /passive /norestart"
        } else {
            "start /wait \"\" \"%~1\" /SILENT"
        }

        val restartCmd = if (appPath.isNotBlank() && appPath.endsWith(".exe", ignoreCase = true) && !appPath.endsWith("java.exe", ignoreCase = true)) {
            "if exist \"%~2\" start \"\" \"%~2\""
        } else {
            ""
        }

        val batContent = buildString {
            appendLine("@echo off")
            appendLine("ping -n 3 127.0.0.1 > nul")
            appendLine(installCmd)
            if (restartCmd.isNotBlank()) {
                appendLine(restartCmd)
            }
            appendLine("(goto) 2>nul & del \"%~1\" & del \"%~f0\"")
        }

        batFile.writeText(batContent)

        ProcessBuilder("cmd.exe", "/c", batFile.absolutePath, installerFile.absolutePath, appPath)
            .start()

        exitProcess(0)
    }
}

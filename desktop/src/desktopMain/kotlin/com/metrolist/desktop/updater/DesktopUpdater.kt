package com.metrolist.desktop.updater

import com.metrolist.desktop.BuildInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
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
import java.security.MessageDigest
import java.time.Duration
import kotlin.system.exitProcess

@Serializable
data class UpdateFileEntry(
    val path: String,       // Relative path from install root, e.g. "app/Echo.cfg" or "app/desktop-xxx.jar"
    val sha256: String,
    val size: Long,
    val downloadUrl: String,
)

@Serializable
data class UpdateManifest(
    val version: String,
    val build: Int,
    val tagName: String,
    val releaseUrl: String,
    val runtimeHash: String,
    val msiUrl: String? = null,
    val msiSha256: String? = null,
    val files: List<UpdateFileEntry> = emptyList(),
)

enum class UpdateStrategy {
    HYBRID_FILES, // JRE runtime matches; only app/ code & config files are updated
    FULL_MSI,     // JRE runtime changed or fallback; runs per-user MSI installer
}

data class StagedUpdate(
    val manifest: UpdateManifest,
    val strategy: UpdateStrategy,
    val stagingDir: File,
    val filesToUpdate: List<UpdateFileEntry>,
    val obsoleteJars: List<String>,
    val msiInstallerFile: File? = null,
)

data class DesktopReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val description: String,
    val releaseUrl: String,
    val downloadUrl: String?,
    val publishedAt: String,
    val sha256: String? = null,
    val build: Int = 1,
    val manifest: UpdateManifest? = null,
    val strategy: UpdateStrategy = UpdateStrategy.HYBRID_FILES,
)

object DesktopUpdater {
    const val CURRENT_VERSION: String = BuildInfo.VERSION
    const val CURRENT_BUILD: Int = BuildInfo.BUILD
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

    @Volatile
    private var trayIcon: TrayIcon? = null
    private var trayRemovalJob: Job? = null
    private val updaterScope = CoroutineScope(Dispatchers.Default)

    // Reactive states for UI
    private val _isDownloadingUpdate = MutableStateFlow(false)
    val isDownloadingUpdate: StateFlow<Boolean> = _isDownloadingUpdate.asStateFlow()

    private val _updateDownloadProgress = MutableStateFlow(0f)
    val updateDownloadProgress: StateFlow<Float> = _updateDownloadProgress.asStateFlow()

    private val _isUpdateStaged = MutableStateFlow(false)
    val isUpdateStaged: StateFlow<Boolean> = _isUpdateStaged.asStateFlow()

    private val _stagedReleaseInfo = MutableStateFlow<DesktopReleaseInfo?>(null)
    val stagedReleaseInfo: StateFlow<DesktopReleaseInfo?> = _stagedReleaseInfo.asStateFlow()

    var activeStagedUpdate: StagedUpdate? = null
        private set

    /**
     * Resolves the root directory of the Echo desktop installation.
     */
    fun getAppInstallationDir(): File {
        val commandPath = ProcessHandle.current().info().command().orElse("")
        if (commandPath.isNotBlank()) {
            val exe = File(commandPath)
            if (exe.exists() && exe.name.endsWith(".exe", ignoreCase = true) && !exe.name.startsWith("java", ignoreCase = true)) {
                return exe.parentFile
            }
        }
        val localAppData = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        val perUserDir = File(localAppData, "Echo")
        if (perUserDir.exists()) return perUserDir

        return File(".")
    }

    /**
     * Calculates a deterministic SHA-256 hash for the JRE runtime directory.
     */
    fun calculateRuntimeHash(runtimeDir: File): String {
        if (!runtimeDir.exists() || !runtimeDir.isDirectory) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        runtimeDir.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(runtimeDir).path.replace('\\', '/') }
            .forEach { f ->
                val rel = f.relativeTo(runtimeDir).path.replace('\\', '/')
                digest.update(rel.toByteArray(Charsets.UTF_8))
                digest.update(f.length().toString().toByteArray(Charsets.UTF_8))
                f.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var r: Int
                    while (input.read(buf).also { r = it } != -1) {
                        digest.update(buf, 0, r)
                    }
                }
            }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Compares two semantic version strings.
     * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
     */
    fun compareVersions(v1: String, v2: String): Int {
        val clean1 = v1.removePrefix("v").trim()
        val clean2 = v2.removePrefix("v").trim()

        val v1Parts = clean1.split(".", "-").mapNotNull { it.toIntOrNull() }
        val v2Parts = clean2.split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(v1Parts.size, v2Parts.size)

        for (i in 0 until maxLen) {
            val p1 = v1Parts.getOrNull(i) ?: 0
            val p2 = v2Parts.getOrNull(i) ?: 0
            if (p1 > p2) return 1
            if (p1 < p2) return -1
        }

        // If numeric parts are equal, a pre-release version has lower precedence
        val v1HasPre = clean1.contains("-")
        val v2HasPre = clean2.contains("-")
        if (v1HasPre && !v2HasPre) return -1
        if (!v1HasPre && v2HasPre) return 1
        if (v1HasPre && v2HasPre) {
            val pre1 = clean1.substringAfter("-")
            val pre2 = clean2.substringAfter("-")
            return pre1.compareTo(pre2)
        }

        return 0
    }

    fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        val cleanCurrent = currentVersion.removePrefix("v").trim()
        val cleanLatest = latestVersion.removePrefix("v").trim()
        if (cleanCurrent.startsWith("1.") && cleanLatest.startsWith("13.")) {
            return false
        }
        return compareVersions(latestVersion, currentVersion) > 0
    }

    fun isBuildUpdateAvailable(currentBuild: Int, latestBuild: Int): Boolean {
        return latestBuild > currentBuild
    }

    internal fun parseManifest(manifestJson: String): UpdateManifest? {
        return runCatching { json.decodeFromString<UpdateManifest>(manifestJson) }.getOrNull()
    }

    internal fun parseRelease(jsonString: String): DesktopReleaseInfo? {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val tagName = root["tag_name"]?.jsonPrimitive?.content ?: return null
        val versionName = tagName.removePrefix("v").substringBefore("-b")
        val title = root["name"]?.jsonPrimitive?.content ?: tagName
        val description = root["body"]?.jsonPrimitive?.content ?: ""
        val releaseUrl = root["html_url"]?.jsonPrimitive?.content ?: "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases"
        val publishedAt = root["published_at"]?.jsonPrimitive?.content ?: ""

        val assets = root["assets"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() } ?: emptyList()

        // Check if update-manifest.json exists among release assets
        val manifestAsset = assets.firstOrNull {
            it["name"]?.jsonPrimitive?.content?.equals("update-manifest.json", ignoreCase = true) == true
        }
        val manifestDownloadUrl = manifestAsset?.get("browser_download_url")?.jsonPrimitive?.content

        // Deterministic priority for standalone installer fallback: .msi -> .exe -> .zip
        val installerCandidates = assets.filter { asset ->
            val name = asset["name"]?.jsonPrimitive?.content?.lowercase() ?: ""
            (name.endsWith(".msi") || name.endsWith(".exe") || name.endsWith(".zip")) &&
                    !name.endsWith(".sha256") && !name.endsWith(".blockmap") && !name.endsWith(".sig") && !name.endsWith(".asc")
        }

        val chosenAsset = installerCandidates.filter {
            it["name"]?.jsonPrimitive?.content?.lowercase()?.endsWith(".msi") == true
        }.let { msiList ->
            if (msiList.isNotEmpty()) {
                msiList.firstOrNull { it["name"]?.jsonPrimitive?.content?.contains("echo", ignoreCase = true) == true } ?: msiList.first()
            } else {
                val exeList = installerCandidates.filter {
                    it["name"]?.jsonPrimitive?.content?.lowercase()?.endsWith(".exe") == true
                }
                if (exeList.isNotEmpty()) {
                    exeList.firstOrNull { it["name"]?.jsonPrimitive?.content?.contains("echo", ignoreCase = true) == true } ?: exeList.first()
                } else {
                    installerCandidates.firstOrNull {
                        it["name"]?.jsonPrimitive?.content?.lowercase()?.endsWith(".zip") == true
                    }
                }
            }
        }

        val downloadUrl = manifestDownloadUrl ?: chosenAsset?.get("browser_download_url")?.jsonPrimitive?.content ?: releaseUrl

        val rawDigest = chosenAsset?.get("digest")?.jsonPrimitive?.contentOrNull
        val sha256 = when {
            rawDigest == null -> null
            rawDigest.startsWith("sha256:", ignoreCase = true) -> rawDigest.substringAfter(":").trim()
            rawDigest.length == 64 && rawDigest.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } -> rawDigest.trim()
            else -> null
        }

        val parsedBuild = tagName.substringAfter("-b", "").toIntOrNull() ?: 1

        return DesktopReleaseInfo(
            tagName = tagName,
            versionName = versionName,
            title = title,
            description = description,
            releaseUrl = releaseUrl,
            downloadUrl = downloadUrl,
            publishedAt = publishedAt,
            sha256 = sha256,
            build = parsedBuild,
            strategy = UpdateStrategy.HYBRID_FILES,
        )
    }

    internal fun findCompanionShaUrl(jsonString: String, installerDownloadUrl: String?): String? {
        if (installerDownloadUrl == null) return null
        val root = runCatching { json.parseToJsonElement(jsonString).jsonObject }.getOrNull() ?: return null
        val assets = root["assets"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() } ?: emptyList()
        val installerName = installerDownloadUrl.substringAfterLast("/").substringBefore("?").lowercase()

        val companion = assets.firstOrNull { asset ->
            val name = asset["name"]?.jsonPrimitive?.content?.lowercase() ?: ""
            name == "$installerName.sha256" || name == "${installerName.substringBeforeLast('.')}.sha256"
        } ?: assets.firstOrNull { asset ->
            val name = asset["name"]?.jsonPrimitive?.content?.lowercase() ?: ""
            name.endsWith(".sha256")
        }

        return companion?.get("browser_download_url")?.jsonPrimitive?.content
    }

    internal fun extractSha256(text: String): String? {
        return text.trim()
            .split("\\s+".toRegex())
            .firstOrNull { token ->
                token.length == 64 && token.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
            }
    }

    internal fun verifyFileSha256(file: File, expectedHash: String): Boolean {
        if (!file.exists() || !file.isFile) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        return actualHash.equals(expectedHash.trim(), ignoreCase = true)
    }

    internal fun verifyAndProcessInstaller(tempFile: File, expectedSha256: String?) {
        if (expectedSha256.isNullOrBlank()) {
            tempFile.delete()
            throw RuntimeException("Checksum installer tidak tersedia")
        }
        if (!verifyFileSha256(tempFile, expectedSha256)) {
            tempFile.delete()
            throw RuntimeException("Checksum installer tidak cocok")
        }
    }

    suspend fun checkForUpdate(forceRefresh: Boolean = false): Result<DesktopReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = System.currentTimeMillis()
                if (!forceRefresh && cachedReleaseInfo != null && (now - lastCheckTime) < CHECK_INTERVAL_MS) {
                    val hasUpdate = isBuildUpdateAvailable(CURRENT_BUILD, cachedReleaseInfo!!.build) ||
                            isUpdateAvailable(CURRENT_VERSION, cachedReleaseInfo!!.versionName)
                    return@runCatching if (hasUpdate) cachedReleaseInfo else null
                }

                val request = HttpRequest.newBuilder()
                    .uri(URI.create(LATEST_RELEASE_API_URL))
                    .header("User-Agent", "Echo-Desktop/$CURRENT_VERSION (Build $CURRENT_BUILD)")
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

                if (response.statusCode() == 404) {
                    return@runCatching null
                }

                if (response.statusCode() !in 200..299) {
                    throw RuntimeException("GitHub API HTTP ${response.statusCode()}")
                }

                val body = response.body()
                var releaseInfo = parseRelease(body) ?: return@runCatching null

                // Check if update-manifest.json is available in release
                val rootJson = json.parseToJsonElement(body).jsonObject
                val assets = rootJson["assets"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() } ?: emptyList()
                val manifestAsset = assets.firstOrNull {
                    it["name"]?.jsonPrimitive?.content?.equals("update-manifest.json", ignoreCase = true) == true
                }

                if (manifestAsset != null) {
                    val manifestUrl = manifestAsset["browser_download_url"]?.jsonPrimitive?.content
                    if (manifestUrl != null) {
                        try {
                            val manifestReq = HttpRequest.newBuilder()
                                .uri(URI.create(manifestUrl))
                                .header("User-Agent", "Echo-Desktop/$CURRENT_VERSION")
                                .timeout(Duration.ofSeconds(15))
                                .GET()
                                .build()
                            val manifestResp = httpClient.send(manifestReq, HttpResponse.BodyHandlers.ofString())
                            if (manifestResp.statusCode() in 200..299) {
                                val manifest = parseManifest(manifestResp.body())
                                if (manifest != null) {
                                    val installDir = getAppInstallationDir()
                                    val localRuntimeDir = File(installDir, "runtime")
                                    val localRuntimeHash = if (localRuntimeDir.exists()) calculateRuntimeHash(localRuntimeDir) else ""

                                    val runtimeChanged = manifest.runtimeHash.isNotBlank() &&
                                            localRuntimeHash.isNotBlank() &&
                                            !manifest.runtimeHash.equals(localRuntimeHash, ignoreCase = true)

                                    val strategy = if (runtimeChanged) UpdateStrategy.FULL_MSI else UpdateStrategy.HYBRID_FILES

                                    releaseInfo = releaseInfo.copy(
                                        build = manifest.build,
                                        versionName = manifest.version,
                                        manifest = manifest,
                                        strategy = strategy,
                                        downloadUrl = if (strategy == UpdateStrategy.FULL_MSI) manifest.msiUrl else manifestUrl,
                                        sha256 = if (strategy == UpdateStrategy.FULL_MSI) manifest.msiSha256 else null
                                    )
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                } else if (releaseInfo.sha256 == null && releaseInfo.downloadUrl != null && releaseInfo.downloadUrl != releaseInfo.releaseUrl) {
                    // Fallback: check companion .sha256 asset
                    val companionUrl = findCompanionShaUrl(body, releaseInfo.downloadUrl)
                    if (companionUrl != null) {
                        try {
                            val shaRequest = HttpRequest.newBuilder()
                                .uri(URI.create(companionUrl))
                                .header("User-Agent", "Echo-Desktop/$CURRENT_VERSION")
                                .timeout(Duration.ofSeconds(10))
                                .GET()
                                .build()
                            val shaResponse = httpClient.send(shaRequest, HttpResponse.BodyHandlers.ofString())
                            if (shaResponse.statusCode() in 200..299) {
                                val companionHash = extractSha256(shaResponse.body())
                                if (companionHash != null) {
                                    releaseInfo = releaseInfo.copy(sha256 = companionHash)
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                }

                cachedReleaseInfo = releaseInfo
                lastCheckTime = now

                val isNew = isBuildUpdateAvailable(CURRENT_BUILD, releaseInfo.build) ||
                        isUpdateAvailable(CURRENT_VERSION, releaseInfo.versionName)

                if (isNew) {
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

    fun showDesktopNotification(
        title: String,
        message: String,
        onNotificationClick: (() -> Unit)? = null,
    ) {
        try {
            if (!SystemTray.isSupported()) return
            val tray = SystemTray.getSystemTray()

            synchronized(this) {
                var icon = trayIcon
                if (icon == null) {
                    val image = Toolkit.getDefaultToolkit().createImage(
                        DesktopUpdater::class.java.getResource("/icons/icon.png")
                    )
                    icon = TrayIcon(image, "Echo").apply {
                        isImageAutoSize = true
                    }
                    trayIcon = icon
                }

                for (listener in icon.actionListeners) {
                    icon.removeActionListener(listener)
                }
                if (onNotificationClick != null) {
                    icon.addActionListener { onNotificationClick() }
                }

                val currentIcons = tray.trayIcons
                if (!currentIcons.contains(icon)) {
                    tray.add(icon)
                }

                icon.displayMessage(title, message, TrayIcon.MessageType.INFO)

                trayRemovalJob?.cancel()
                trayRemovalJob = updaterScope.launch {
                    delay(10_000L)
                    synchronized(DesktopUpdater) {
                        try {
                            tray.remove(icon)
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (_: Throwable) {
            // Silently fallback if system tray is not available
        }
    }

    /**
     * Downloads and stages the update in the background.
     */
    suspend fun downloadAndStageUpdate(
        releaseInfo: DesktopReleaseInfo,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            _isDownloadingUpdate.value = true
            _updateDownloadProgress.value = 0f

            val installDir = getAppInstallationDir()

            if (releaseInfo.strategy == UpdateStrategy.FULL_MSI || releaseInfo.manifest == null) {
                // Skenario Fallback MSI: Download per-user MSI installer
                val msiUrl = releaseInfo.downloadUrl ?: releaseInfo.releaseUrl
                val expectedSha256 = releaseInfo.sha256
                    ?: throw RuntimeException("Checksum installer MSI tidak tersedia")

                val tempFile = File.createTempFile("echo_msi_update_", ".msi")
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(msiUrl))
                    .header("User-Agent", "Echo-Desktop/$CURRENT_VERSION")
                    .timeout(Duration.ofMinutes(15))
                    .GET()
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
                if (response.statusCode() !in 200..299) {
                    throw RuntimeException("Gagal mengunduh installer (HTTP ${response.statusCode()})")
                }

                val contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L)
                val digest = MessageDigest.getInstance("SHA-256")

                try {
                    response.body().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var totalRead = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                digest.update(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (contentLength > 0) {
                                    val prog = (totalRead.toDouble() / contentLength.toDouble()).toFloat().coerceIn(0f, 1f)
                                    _updateDownloadProgress.value = prog
                                    onProgress(prog)
                                }
                            }
                            output.flush()

                            if (contentLength > 0 && totalRead != contentLength) {
                                throw RuntimeException("Unduhan tidak lengkap")
                            }
                        }
                    }

                    val computedHash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!computedHash.equals(expectedSha256.trim(), ignoreCase = true)) {
                        throw RuntimeException("Checksum installer tidak cocok")
                    }

                    activeStagedUpdate = StagedUpdate(
                        manifest = releaseInfo.manifest ?: UpdateManifest(
                            version = releaseInfo.versionName,
                            build = releaseInfo.build,
                            tagName = releaseInfo.tagName,
                            releaseUrl = releaseInfo.releaseUrl,
                            runtimeHash = "",
                            msiUrl = msiUrl,
                            msiSha256 = expectedSha256
                        ),
                        strategy = UpdateStrategy.FULL_MSI,
                        stagingDir = tempFile.parentFile,
                        filesToUpdate = emptyList(),
                        obsoleteJars = emptyList(),
                        msiInstallerFile = tempFile
                    )
                    _isUpdateStaged.value = true
                    _stagedReleaseInfo.value = releaseInfo
                } catch (e: Throwable) {
                    tempFile.delete()
                    throw e
                }
            } else {
                // Skenario Hybrid Files: Hanya unduh file app\ yang berubah
                val manifest = releaseInfo.manifest
                val stagingDir = File(installDir, "staging_b${manifest.build}")
                stagingDir.deleteRecursively()
                stagingDir.mkdirs()

                // Identifikasi file lokal yang berbeda
                val filesToUpdate = manifest.files.filter { entry ->
                    val fileName = File(entry.path).name
                    if (fileName.startsWith(".")) return@filter false
                    val localFile = File(installDir, entry.path)
                    !localFile.exists() || localFile.length() != entry.size || !verifyFileSha256(localFile, entry.sha256)
                }

                // Identifikasi jar lama yang obsolete
                val localAppDir = File(installDir, "app")
                val manifestJarNames = manifest.files.map { File(it.path).name }.toSet()
                val obsoleteJars = if (localAppDir.exists()) {
                    localAppDir.listFiles { _, name -> name.endsWith(".jar", ignoreCase = true) }
                        ?.map { it.name }
                        ?.filter { it !in manifestJarNames } ?: emptyList()
                } else emptyList()

                val totalBytes = filesToUpdate.sumOf { it.size }
                var downloadedBytes = 0L

                try {
                    for (entry in filesToUpdate) {
                        val targetStagedFile = File(stagingDir, entry.path)
                        targetStagedFile.parentFile.mkdirs()

                        val req = HttpRequest.newBuilder()
                            .uri(URI.create(entry.downloadUrl))
                            .header("User-Agent", "Echo-Desktop/$CURRENT_VERSION")
                            .timeout(Duration.ofMinutes(10))
                            .GET()
                            .build()

                        val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream())
                        if (resp.statusCode() !in 200..299) {
                            throw RuntimeException("Gagal mengunduh ${entry.path} (HTTP ${resp.statusCode()})")
                        }

                        val fileDigest = MessageDigest.getInstance("SHA-256")
                        resp.body().use { inStream ->
                            targetStagedFile.outputStream().use { outStream ->
                                val buf = ByteArray(64 * 1024)
                                var readBytes: Int
                                while (inStream.read(buf).also { readBytes = it } != -1) {
                                    outStream.write(buf, 0, readBytes)
                                    fileDigest.update(buf, 0, readBytes)
                                    downloadedBytes += readBytes
                                    if (totalBytes > 0) {
                                        val p = (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
                                        _updateDownloadProgress.value = p
                                        onProgress(p)
                                    }
                                }
                                outStream.flush()
                            }
                        }

                        val hash = fileDigest.digest().joinToString("") { "%02x".format(it) }
                        if (!hash.equals(entry.sha256.trim(), ignoreCase = true)) {
                            targetStagedFile.delete()
                            throw RuntimeException("Verifikasi SHA-256 gagal untuk file ${entry.path}")
                        }
                    }

                    // Tulis daftar jar usang ke cleanup_jars.txt jika ada
                    if (obsoleteJars.isNotEmpty()) {
                        File(stagingDir, "cleanup_jars.txt").writeText(obsoleteJars.joinToString("\n"))
                    }

                    activeStagedUpdate = StagedUpdate(
                        manifest = manifest,
                        strategy = UpdateStrategy.HYBRID_FILES,
                        stagingDir = stagingDir,
                        filesToUpdate = filesToUpdate,
                        obsoleteJars = obsoleteJars
                    )
                    _isUpdateStaged.value = true
                    _stagedReleaseInfo.value = releaseInfo
                } catch (e: Throwable) {
                    stagingDir.deleteRecursively()
                    throw e
                }
            }
        }.also {
            _isDownloadingUpdate.value = false
        }
    }

    /**
     * Terapkan update dan restart Echo.
     */
    fun applyUpdateAndRestart() {
        val staged = activeStagedUpdate ?: return
        val currentPid = ProcessHandle.current().pid()
        val appPath = ProcessHandle.current().info().command().orElse("")
        val installDir = getAppInstallationDir()
        val tempDir = System.getProperty("java.io.tmpdir")
        val batFile = File(tempDir, "echo_apply_update_${System.currentTimeMillis()}.bat")

        if (staged.strategy == UpdateStrategy.FULL_MSI) {
            val msiFile = staged.msiInstallerFile ?: return
            val batContent = buildString {
                appendLine("@echo off")
                appendLine("set PID=%~3")
                appendLine(":wait")
                appendLine("if not \"%PID%\"==\"\" (")
                appendLine("    tasklist /FI \"PID eq %PID%\" 2>nul | find \"%PID%\" >nul && (ping -n 2 127.0.0.1 >nul & goto wait)")
                appendLine(")")
                appendLine("start /wait msiexec /i \"%~1\" /passive /norestart")
                appendLine("set EXIT_CODE=%ERRORLEVEL%")
                appendLine("if \"%EXIT_CODE%\"==\"1638\" (")
                appendLine("    start /wait msiexec /i \"%~1\" REINSTALL=ALL REINSTALLMODE=vomus /passive /norestart")
                appendLine("    set EXIT_CODE=%ERRORLEVEL%")
                appendLine(")")
                appendLine("if not \"%EXIT_CODE%\"==\"0\" if not \"%EXIT_CODE%\"==\"3010\" (")
                appendLine("    echo [%date% %time%] MSI update failed with exit code %EXIT_CODE% >> \"%TEMP%\\echo_update.log\"")
                appendLine(")")
                if (appPath.isNotBlank() && appPath.endsWith(".exe", ignoreCase = true) && !appPath.endsWith("java.exe", ignoreCase = true)) {
                    appendLine("if exist \"%~2\" start \"\" \"%~2\"")
                }
                appendLine("(goto) 2>nul & del \"%~1\" & del \"%~f0\"")
            }

            batFile.writeText(batContent)
            ProcessBuilder("cmd.exe", "/c", batFile.absolutePath, msiFile.absolutePath, appPath, currentPid.toString()).start()
            exitProcess(0)
        } else {
            // Skenario Hybrid: Swap app/ files dengan backup dan rollback
            val batContent = buildString {
                appendLine("@echo off")
                appendLine("setlocal enabledelayedexpansion")
                appendLine("set INSTALL_DIR=%~1")
                appendLine("set STAGING_DIR=%~2")
                appendLine("set PID=%~3")
                appendLine("set APP_EXE=%~4")
                appendLine("set LOG_FILE=%TEMP%\\echo_update.log")
                appendLine("echo [%date% %time%] Memulai swap pembaruan Echo untuk PID %PID% >> \"%LOG_FILE%\"")
                appendLine(":wait_loop")
                appendLine("if not \"%PID%\"==\"\" (")
                appendLine("    tasklist /FI \"PID eq %PID%\" 2>nul | find \"%PID%\" >nul && (")
                appendLine("        ping -n 2 127.0.0.1 >nul")
                appendLine("        goto wait_loop")
                appendLine("    )")
                appendLine(")")
                appendLine("set APP_DIR=%INSTALL_DIR%\\app")
                appendLine("set BACKUP_DIR=%INSTALL_DIR%\\app_backup")
                appendLine("if exist \"%BACKUP_DIR%\" rd /s /q \"%BACKUP_DIR%\" 2>nul")
                appendLine("md \"%BACKUP_DIR%\" 2>nul")
                appendLine("xcopy \"%APP_DIR%\" \"%BACKUP_DIR%\" /E /I /Y /Q >nul 2>> \"%LOG_FILE%\"")
                appendLine("if errorlevel 1 (")
                appendLine("    echo [%date% %time%] Backup gagal! Membatalkan update. >> \"%LOG_FILE%\"")
                appendLine("    goto rollback_or_restart")
                appendLine(")")
                appendLine("echo [%date% %time%] Menyalin file pembaruan dari staging... >> \"%LOG_FILE%\"")
                appendLine("xcopy \"%STAGING_DIR%\" \"%INSTALL_DIR%\" /E /I /Y /Q >nul 2>> \"%LOG_FILE%\"")
                appendLine("if errorlevel 1 (")
                appendLine("    echo [%date% %time%] Salin file baru gagal! Melakukan rollback... >> \"%LOG_FILE%\"")
                appendLine("    goto do_rollback")
                appendLine(")")
                appendLine("if exist \"%STAGING_DIR%\\cleanup_jars.txt\" (")
                appendLine("    for /f \"usebackq delims=\" %%f in (\"%STAGING_DIR%\\cleanup_jars.txt\") do (")
                appendLine("        if exist \"%INSTALL_DIR%\\app\\%%f\" (")
                appendLine("            echo [%date% %time%] Menghapus jar usang %%f >> \"%LOG_FILE%\"")
                appendLine("            del /f /q \"%INSTALL_DIR%\\app\\%%f\" 2>nul")
                appendLine("        )")
                appendLine("    )")
                appendLine(")")
                appendLine("rd /s /q \"%STAGING_DIR%\" 2>nul")
                appendLine("rd /s /q \"%BACKUP_DIR%\" 2>nul")
                appendLine("echo [%date% %time%] Pembaruan berhasil diterapkan! >> \"%LOG_FILE%\"")
                appendLine("goto launch_app")
                appendLine(":do_rollback")
                appendLine("echo [%date% %time%] Melakukan rollback... >> \"%LOG_FILE%\"")
                appendLine("xcopy \"%BACKUP_DIR%\" \"%APP_DIR%\" /E /I /Y /Q >nul 2>> \"%LOG_FILE%\"")
                appendLine("rd /s /q \"%BACKUP_DIR%\" 2>nul")
                appendLine("rd /s /q \"%STAGING_DIR%\" 2>nul")
                appendLine(":rollback_or_restart")
                appendLine(":launch_app")
                appendLine("if exist \"%APP_EXE%\" start \"\" \"%APP_EXE%\"")
                appendLine("(goto) 2>nul & del \"%~f0\"")
            }

            batFile.writeText(batContent)
            ProcessBuilder("cmd.exe", "/c", batFile.absolutePath, installDir.absolutePath, staged.stagingDir.absolutePath, currentPid.toString(), appPath).start()
            exitProcess(0)
        }
    }

    // Backward-compatible methods
    suspend fun downloadAndInstallUpdate(
        releaseInfo: DesktopReleaseInfo,
        onProgress: (Float) -> Unit,
    ): Result<Unit> {
        val res = downloadAndStageUpdate(releaseInfo, onProgress)
        if (res.isSuccess) {
            applyUpdateAndRestart()
        }
        return res
    }

    suspend fun downloadAndInstallUpdate(
        downloadUrl: String,
        expectedSha256: String? = null,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val rel = DesktopReleaseInfo(
            tagName = "v$CURRENT_VERSION",
            versionName = CURRENT_VERSION,
            title = "Update",
            description = "",
            releaseUrl = downloadUrl,
            downloadUrl = downloadUrl,
            publishedAt = "",
            sha256 = expectedSha256,
            strategy = UpdateStrategy.FULL_MSI
        )
        downloadAndInstallUpdate(rel, onProgress)
    }

    fun installAndRestart(installerFile: File) {
        val currentPid = ProcessHandle.current().pid()
        val appPath = ProcessHandle.current().info().command().orElse("")
        val isMsi = installerFile.name.endsWith(".msi", ignoreCase = true)
        val tempDir = System.getProperty("java.io.tmpdir")
        val batFile = File(tempDir, "echo_updater_${System.currentTimeMillis()}.bat")

        val installCmd = if (isMsi) {
            "start /wait msiexec /i \"%~1\" /passive /norestart"
        } else {
            "start /wait \"\" \"%~1\""
        }

        val restartCmd = if (appPath.isNotBlank() && appPath.endsWith(".exe", ignoreCase = true) && !appPath.endsWith("java.exe", ignoreCase = true)) {
            "if exist \"%~2\" start \"\" \"%~2\""
        } else {
            ""
        }

        val batContent = buildString {
            appendLine("@echo off")
            appendLine("set PID=%~3")
            appendLine(":wait")
            appendLine("if not \"%PID%\"==\"\" (")
            appendLine("    tasklist /FI \"PID eq %PID%\" 2>nul | find \"%PID%\" >nul && (ping -n 2 127.0.0.1 >nul & goto wait)")
            appendLine(")")
            appendLine(installCmd)
            appendLine("set EXIT_CODE=%ERRORLEVEL%")
            appendLine("if not \"%EXIT_CODE%\"==\"0\" if not \"%EXIT_CODE%\"==\"3010\" (")
            appendLine("    echo [%date% %time%] Update failed with exit code %EXIT_CODE% >> \"%TEMP%\\echo_update.log\"")
            appendLine(")")
            if (restartCmd.isNotBlank()) {
                appendLine(restartCmd)
            }
            appendLine("(goto) 2>nul & del \"%~1\" & del \"%~f0\"")
        }

        batFile.writeText(batContent)

        ProcessBuilder("cmd.exe", "/c", batFile.absolutePath, installerFile.absolutePath, appPath, currentPid.toString())
            .start()

        exitProcess(0)
    }
}

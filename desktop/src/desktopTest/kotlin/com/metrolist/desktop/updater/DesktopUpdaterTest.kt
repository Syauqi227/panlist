package com.metrolist.desktop.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class DesktopUpdaterTest {

    @Test
    fun testCompareVersions() {
        // v1 < v2 -> -1
        assertEquals(-1, DesktopUpdater.compareVersions("1.3.0", "1.3.1"))
        // v1 > v2 -> 1
        assertEquals(1, DesktopUpdater.compareVersions("v1.4", "1.3.9"))
        // Equal versions with different precision/prefix
        assertEquals(0, DesktopUpdater.compareVersions("1.3.0", "1.3.0.0"))
        assertEquals(0, DesktopUpdater.compareVersions("v1.3.0", "1.3.0"))
        // Pre-release versions have lower precedence than final release
        assertEquals(-1, DesktopUpdater.compareVersions("1.3.0-beta", "1.3.0"))
        assertEquals(1, DesktopUpdater.compareVersions("1.3.0", "1.3.0-beta"))
        assertEquals(0, DesktopUpdater.compareVersions("1.3.0-beta", "1.3.0-beta"))
    }

    @Test
    fun testIsUpdateAvailable() {
        assertFalse(DesktopUpdater.isUpdateAvailable("1.3.0", "1.3.0"))
        assertFalse(DesktopUpdater.isUpdateAvailable("1.3.0", "1.2.9"))
        assertFalse(DesktopUpdater.isUpdateAvailable("1.4.0", "1.3.9"))
        assertFalse(DesktopUpdater.isUpdateAvailable("1.3.0", "13.7.5"))

        assertTrue(DesktopUpdater.isUpdateAvailable("1.3.0", "1.3.1"))
        assertTrue(DesktopUpdater.isUpdateAvailable("1.3.0", "1.4.0"))
        assertTrue(DesktopUpdater.isUpdateAvailable("1.3.0", "14.0.0"))
        assertTrue(DesktopUpdater.isUpdateAvailable("1.3.0-beta", "1.3.0"))
    }

    @Test
    fun testIsBuildUpdateAvailable() {
        assertTrue(DesktopUpdater.isBuildUpdateAvailable(1, 2))
        assertTrue(DesktopUpdater.isBuildUpdateAvailable(5, 10))
        assertFalse(DesktopUpdater.isBuildUpdateAvailable(2, 2))
        assertFalse(DesktopUpdater.isBuildUpdateAvailable(3, 2))
    }

    @Test
    fun testParseUpdateManifest() {
        val manifestJson = """
            {
                "version": "1.3.0",
                "build": 2,
                "tagName": "v1.3.0-b2",
                "releaseUrl": "https://github.com/Syauqi227/panlist/releases/tag/v1.3.0-b2",
                "runtimeHash": "runtime_hash_abc123",
                "msiUrl": "https://example.com/Echo-1.3.0.msi",
                "msiSha256": "msi_sha256_def456",
                "files": [
                    {
                        "path": "app/Echo.cfg",
                        "sha256": "cfg_sha256_789",
                        "size": 4096,
                        "downloadUrl": "https://example.com/Echo.cfg"
                    },
                    {
                        "path": "app/desktop-1.3.0.jar",
                        "sha256": "jar_sha256_012",
                        "size": 1350000,
                        "downloadUrl": "https://example.com/desktop-1.3.0.jar"
                    }
                ]
            }
        """.trimIndent()

        val manifest = DesktopUpdater.parseManifest(manifestJson)
        assertNotNull(manifest)
        assertEquals("1.3.0", manifest?.version)
        assertEquals(2, manifest?.build)
        assertEquals("runtime_hash_abc123", manifest?.runtimeHash)
        assertEquals(2, manifest?.files?.size)
        assertEquals("app/Echo.cfg", manifest?.files?.get(0)?.path)
        assertEquals("app/desktop-1.3.0.jar", manifest?.files?.get(1)?.path)
    }

    @Test
    fun testCalculateRuntimeHashDeterministic() {
        val tempRuntime = File.createTempFile("test_runtime_", "_dir")
        tempRuntime.delete()
        tempRuntime.mkdirs()

        try {
            val binDir = File(tempRuntime, "bin").apply { mkdirs() }
            val libDir = File(tempRuntime, "lib").apply { mkdirs() }

            File(binDir, "java.dll").writeText("fake-java-dll-content")
            File(libDir, "modules").writeText("fake-modules-content")
            File(tempRuntime, "release").writeText("JAVA_VERSION=21.0.6")

            val hash1 = DesktopUpdater.calculateRuntimeHash(tempRuntime)
            val hash2 = DesktopUpdater.calculateRuntimeHash(tempRuntime)

            assertTrue(hash1.isNotBlank())
            assertEquals(hash1, hash2)

            // Modifying a file must change the hash
            File(binDir, "java.dll").writeText("modified-java-dll-content")
            val hash3 = DesktopUpdater.calculateRuntimeHash(tempRuntime)
            assertFalse(hash1 == hash3)
        } finally {
            tempRuntime.deleteRecursively()
        }
    }

    @Test
    fun testParseReleaseDeterministicAssetSelection() {
        val json = """
            {
                "tag_name": "v1.4.0",
                "name": "Echo 1.4.0",
                "body": "Fixes and improvements",
                "html_url": "https://github.com/Syauqi227/panlist/releases/tag/v1.4.0",
                "published_at": "2026-10-23T12:00:00Z",
                "assets": [
                    {
                        "name": "Echo-1.4.0.zip",
                        "browser_download_url": "https://example.com/Echo-1.4.0.zip"
                    },
                    {
                        "name": "Echo-1.4.0.exe",
                        "browser_download_url": "https://example.com/Echo-1.4.0.exe"
                    },
                    {
                        "name": "Echo-1.4.0.msi",
                        "browser_download_url": "https://example.com/Echo-1.4.0.msi",
                        "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                    }
                ]
            }
        """.trimIndent()

        val release = DesktopUpdater.parseRelease(json)
        assertNotNull(release)
        assertEquals("v1.4.0", release?.tagName)
        assertEquals("1.4.0", release?.versionName)
        assertEquals("https://example.com/Echo-1.4.0.msi", release?.downloadUrl)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", release?.sha256)
    }

    @Test
    fun testParseReleaseExeFallbackWhenNoMsi() {
        val json = """
            {
                "tag_name": "v1.4.0",
                "name": "Echo 1.4.0",
                "body": "",
                "html_url": "https://github.com/Syauqi227/panlist/releases/tag/v1.4.0",
                "assets": [
                    {
                        "name": "Echo-1.4.0.zip",
                        "browser_download_url": "https://example.com/Echo-1.4.0.zip"
                    },
                    {
                        "name": "Echo-1.4.0.exe",
                        "browser_download_url": "https://example.com/Echo-1.4.0.exe"
                    }
                ]
            }
        """.trimIndent()

        val release = DesktopUpdater.parseRelease(json)
        assertNotNull(release)
        assertEquals("https://example.com/Echo-1.4.0.exe", release?.downloadUrl)
    }

    @Test
    fun testParseReleaseWithoutInstallerAssets() {
        val json = """
            {
                "tag_name": "v1.4.0",
                "name": "Echo 1.4.0",
                "body": "",
                "html_url": "https://github.com/Syauqi227/panlist/releases/tag/v1.4.0",
                "assets": [
                    {
                        "name": "source_code.tar.gz",
                        "browser_download_url": "https://example.com/source.tar.gz"
                    }
                ]
            }
        """.trimIndent()

        val release = DesktopUpdater.parseRelease(json)
        assertNotNull(release)
        assertEquals("https://github.com/Syauqi227/panlist/releases/tag/v1.4.0", release?.downloadUrl)
        assertNull(release?.sha256)
    }

    @Test
    fun testExtractSha256AndCompanionUrl() {
        val validHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val textWithFilename = "$validHash  Echo-1.3.0.msi"
        assertEquals(validHash, DesktopUpdater.extractSha256(textWithFilename))

        val textWithAsterisk = "$validHash *Echo-1.3.0.msi"
        assertEquals(validHash, DesktopUpdater.extractSha256(textWithAsterisk))

        val textOnlyHash = "  $validHash\n"
        assertEquals(validHash, DesktopUpdater.extractSha256(textOnlyHash))

        assertNull(DesktopUpdater.extractSha256("invalid hash text"))

        val jsonWithCompanion = """
            {
                "assets": [
                    {
                        "name": "Echo-1.3.0.msi",
                        "browser_download_url": "https://example.com/Echo-1.3.0.msi"
                    },
                    {
                        "name": "Echo-1.3.0.msi.sha256",
                        "browser_download_url": "https://example.com/Echo-1.3.0.msi.sha256"
                    }
                ]
            }
        """.trimIndent()

        val companionUrl = DesktopUpdater.findCompanionShaUrl(jsonWithCompanion, "https://example.com/Echo-1.3.0.msi")
        assertEquals("https://example.com/Echo-1.3.0.msi.sha256", companionUrl)
    }

    @Test
    fun testSha256VerificationSuccess() {
        val tempFile = File.createTempFile("test_installer_", ".msi")
        try {
            val content = "valid installer test binary payload"
            tempFile.writeText(content)

            val digest = MessageDigest.getInstance("SHA-256")
            val expectedHash = digest.digest(content.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            DesktopUpdater.verifyAndProcessInstaller(tempFile, expectedHash)
            assertTrue(tempFile.exists())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testSha256VerificationMismatchDeletesFile() {
        val tempFile = File.createTempFile("test_installer_", ".msi")
        val content = "corrupted installer binary payload"
        tempFile.writeText(content)

        val wrongHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        try {
            DesktopUpdater.verifyAndProcessInstaller(tempFile, wrongHash)
            fail("Should throw RuntimeException on hash mismatch")
        } catch (e: RuntimeException) {
            assertTrue(e.message?.contains("Checksum installer tidak cocok") == true)
            assertFalse("Temp file must be deleted on checksum mismatch", tempFile.exists())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testSha256VerificationMissingHashDeletesFile() {
        val tempFile = File.createTempFile("test_installer_", ".msi")
        tempFile.writeText("some binary")

        try {
            DesktopUpdater.verifyAndProcessInstaller(tempFile, null)
            fail("Should throw RuntimeException when hash is null")
        } catch (e: RuntimeException) {
            assertTrue(e.message?.contains("Checksum installer tidak tersedia") == true)
            assertFalse(tempFile.exists())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testUpdaterPreferencesPersistence() {
        val tempFile = File.createTempFile("test_updater_prefs_", ".properties")
        tempFile.delete()

        try {
            val prefs1 = UpdaterPreferences(tempFile)
            assertTrue(prefs1.autoCheck)
            assertTrue(prefs1.notifications)
            assertEquals("", prefs1.dismissedTag)

            prefs1.dismissedTag = "v1.4.0-b2"
            prefs1.autoCheck = false
            prefs1.notifications = false

            val prefs2 = UpdaterPreferences(tempFile)
            assertEquals("v1.4.0-b2", prefs2.dismissedTag)
            assertFalse(prefs2.autoCheck)
            assertFalse(prefs2.notifications)
        } finally {
            tempFile.delete()
        }
    }
}

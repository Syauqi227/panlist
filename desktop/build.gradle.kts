import java.security.MessageDigest

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val appVersion = "1.3.0"
val appBuild = (project.findProperty("app.build") as? String)?.toIntOrNull() ?: 1

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val version = appVersion
    val build = appBuild
    val outputDir = layout.buildDirectory.dir("generated/buildinfo/kotlin")
    inputs.property("version", version)
    inputs.property("build", build)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("com/metrolist/desktop/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.metrolist.desktop

            object BuildInfo {
                const val VERSION = "$version"
                const val BUILD = $build
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    jvmToolchain(21)
    jvm("desktop")

    sourceSets.named("desktopMain") {
        kotlin.srcDir(layout.buildDirectory.dir("generated/buildinfo/kotlin"))
        dependencies {
            implementation(project(":innertube"))
            implementation(libs.ktor.serialization.json)

            // Compose Multiplatform Desktop
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)

            // Material color palette
            implementation(libs.materialKolor)

            // Image loading via Coil 3 (Multiplatform)
            implementation(libs.coil)
            implementation(libs.coil.network.okhttp)

            // Koin DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            // VLCJ Audio Playback Engine
            implementation(libs.vlcj)
            implementation("net.java.dev.jna:jna:5.14.0")
            implementation("net.java.dev.jna:jna-platform:5.14.0")

            // Coroutines & DataStore
            implementation(libs.coroutines.guava)
            implementation(libs.datastore)

            // JetBrains Runtime custom window decorations API
            implementation(libs.jbr.api)
        }
    }
    sourceSets.named("desktopTest") {
        dependencies {
            implementation(libs.junit)
        }
    }
}

tasks.named("compileKotlinDesktop") {
    dependsOn(generateBuildInfo)
}

compose.desktop {
    application {
        mainClass = "com.metrolist.desktop.MainKt"
        jvmArgs += listOf(
            "-Xms64m",
            "-Xmx256m",
            "-XX:+UseG1GC",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:G1PeriodicGCInterval=3000",
            "-XX:MaxMetaspaceSize=128m"
        )

        val jbrHome = project.findProperty("jbr.home") as? String
            ?: System.getenv("JBR_HOME")
            ?: "${System.getProperty("user.home")}/.jdks/jbr-21.0.6"
        if (file(jbrHome).exists()) {
            javaHome = jbrHome
        }

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "Echo"
            modules("java.net.http")
            packageVersion = appVersion
            description = "Echo - YouTube Music Client for Windows"
            vendor = "Echo"
            windows {
                menu = true
                shortcut = true
                upgradeUuid = "347a55d0-be4d-3837-adee-16fcd706749d"
                dirChooser = false
                perUserInstall = true
                iconFile.set(project.file("src/desktopMain/resources/icons/icon.ico"))
            }
        }
    }
}

val generateUpdateManifest = tasks.register("generateUpdateManifest") {
    dependsOn("createDistributable")
    val version = appVersion
    val build = appBuild
    val repoOwner = "Syauqi227"
    val repoName = "panlist"
    val tagName = "v$version-b$build"
    val releaseBaseUrl = "https://github.com/$repoOwner/$repoName/releases"
    val downloadBaseUrl = "$releaseBaseUrl/download/$tagName"

    val distributableDir = layout.buildDirectory.dir("compose/binaries/main/app/Echo")
    val outputManifestFile = layout.buildDirectory.file("update-manifest.json")
    val releaseFilesDir = layout.buildDirectory.dir("update-release-files")
    val msiFilesDir = layout.buildDirectory.dir("compose/binaries/main/msi")

    val prevManifestProperty = providers.gradleProperty("previousManifest")
    val defaultPrevFile = layout.projectDirectory.file("update-manifest-previous.json").asFile
    val prevFile = if (prevManifestProperty.isPresent) File(prevManifestProperty.get()) else if (defaultPrevFile.exists()) defaultPrevFile else null

    outputs.file(outputManifestFile)
    outputs.dir(releaseFilesDir)

    doLast {
        val echoDir = distributableDir.get().asFile
        if (!echoDir.exists()) {
            throw GradleException("Distributable directory not found: ${echoDir.absolutePath}")
        }

        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var r: Int
                while (input.read(buf).also { r = it } != -1) {
                    digest.update(buf, 0, r)
                }
            }
            return digest.digest().joinToString("") { b -> String.format("%02x", b) }
        }

        // 1. Calculate runtimeHash
        val runtimeDir = File(echoDir, "runtime")
        val runtimeHash = if (runtimeDir.exists()) {
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
            digest.digest().joinToString("") { b -> String.format("%02x", b) }
        } else ""

        // 2. Scan app/ directory
        val appDir = File(echoDir, "app")
        val releaseDir = releaseFilesDir.get().asFile
        releaseDir.deleteRecursively()
        releaseDir.mkdirs()

        val prevFilesMap = mutableMapOf<String, String>() // sha256 -> downloadUrl
        if (prevFile != null && prevFile.exists()) {
            try {
                val prevJson = groovy.json.JsonSlurper().parse(prevFile) as? Map<*, *>
                val prevList = prevJson?.get("files") as? List<*>
                prevList?.forEach { item ->
                    if (item is Map<*, *>) {
                        val sha = item["sha256"] as? String
                        val url = item["downloadUrl"] as? String
                        if (sha != null && url != null) {
                            prevFilesMap[sha] = url
                        }
                    }
                }
            } catch (e: Exception) {
                System.err.println("Could not parse previous manifest: ${e.message}")
            }
        }

        val fileEntries = mutableListOf<Map<String, Any>>()
        val changedFiles = mutableListOf<File>()

        if (appDir.exists()) {
            appDir.walkTopDown()
                .filter { it.isFile && it.name != "update-manifest.json" }
                .sortedBy { it.relativeTo(echoDir).path.replace('\\', '/') }
                .forEach { f ->
                    val relPath = f.relativeTo(echoDir).path.replace('\\', '/')
                    val sha = sha256Of(f)
                    val size = f.length()

                    val existingUrl = prevFilesMap[sha]
                    val downloadUrl = existingUrl ?: "$downloadBaseUrl/${f.name}"

                    if (existingUrl == null) {
                        changedFiles.add(f)
                        val targetCopy = File(releaseDir, f.name)
                        f.copyTo(targetCopy, overwrite = true)
                    }

                    fileEntries.add(
                        mapOf(
                            "path" to relPath,
                            "sha256" to sha,
                            "size" to size,
                            "downloadUrl" to downloadUrl
                        )
                    )
                }
        }

        // MSI info if available
        val msiDir = msiFilesDir.get().asFile
        val msiFile = if (msiDir.exists()) msiDir.listFiles()?.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) } else null
        val msiSha = msiFile?.let { sha256Of(it) }
        val msiUrl = msiFile?.let { "$downloadBaseUrl/${it.name}" }

        val manifestMap = linkedMapOf<String, Any?>(
            "version" to version,
            "build" to build,
            "tagName" to tagName,
            "releaseUrl" to "$releaseBaseUrl/tag/$tagName",
            "runtimeHash" to runtimeHash,
            "msiUrl" to msiUrl,
            "msiSha256" to msiSha,
            "files" to fileEntries
        )

        val manifestJson = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(manifestMap))
        val outFile = outputManifestFile.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(manifestJson)

        // Also copy into Echo/app/ and update-release-files/
        File(appDir, "update-manifest.json").writeText(manifestJson)
        File(releaseDir, "update-manifest.json").writeText(manifestJson)

        println("==================================================")
        println("Update Manifest Generated: ${outFile.absolutePath}")
        println("Build: $build | Files in app: ${fileEntries.size} | Changed: ${changedFiles.size}")
        println("Release staging files ready at: ${releaseDir.absolutePath}")
        println("==================================================")
    }
}

tasks.matching { it.name == "packageMsi" }.configureEach {
    finalizedBy(generateUpdateManifest)
}

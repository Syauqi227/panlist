plugins {
    kotlin("multiplatform")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
    jvm("desktop")

    sourceSets.named("desktopMain") {
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
            packageVersion = "13.7.3"
            description = "Echo - YouTube Music Client for Windows"
            vendor = "Echo"
            windows {
                menu = true
                shortcut = true
                upgradeUuid = "347a55d0-be4d-3837-adee-16fcd706749d"
                dirChooser = false
                iconFile.set(project.file("src/desktopMain/resources/icons/icon.ico"))
            }
        }
    }
}

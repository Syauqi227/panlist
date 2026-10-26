package com.metrolist.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import kotlin.math.min
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Toolkit
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import java.io.File
import com.metrolist.desktop.model.*
import com.metrolist.desktop.player.DesktopPlaybackManager
import com.metrolist.desktop.player.RepeatMode
import com.metrolist.desktop.player.VlcAudioPlayer
import com.metrolist.desktop.repository.DesktopMusicRepository
import com.metrolist.desktop.smtc.WindowsMediaControls
import com.metrolist.desktop.ui.AddToPlaylistDialog
import com.metrolist.desktop.ui.DesktopColors
import com.metrolist.desktop.ui.DesktopColorScheme
import com.metrolist.desktop.ui.DesktopRadii
import com.metrolist.desktop.ui.DesktopTypography
import com.metrolist.desktop.ui.PlayingEqualizerBars
import com.metrolist.desktop.ui.PlaylistView
import com.metrolist.desktop.ui.NowPlayingView
import com.metrolist.desktop.ui.DesktopLyricsPageView
import com.metrolist.desktop.ui.SyncedLyricsView
import com.metrolist.desktop.cast.DeviceDiscoveryService
import com.metrolist.desktop.cast.GoogleCastManager
import com.metrolist.desktop.cast.WindowsCastHelper
import com.metrolist.desktop.ui.ConnectDeviceFlyout
import com.metrolist.desktop.ui.UpdateDialog
import com.metrolist.desktop.ui.SongListSkeleton
import com.metrolist.desktop.updater.DesktopReleaseInfo
import com.metrolist.desktop.updater.DesktopUpdater
import com.metrolist.desktop.updater.UpdaterPreferences
import com.metrolist.desktop.discord.DesktopDiscordRpc
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val isJbrWindowDecorationsSupported: Boolean by lazy {
    runCatching { JBR.isWindowDecorationsSupported() }.getOrDefault(false)
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.titleBarDrag(titleBar: WindowDecorations.CustomTitleBar?): Modifier {
    if (titleBar == null) return this
    return this
        .onPointerEvent(PointerEventType.Move) { titleBar.forceHitTest(false) }
        .onPointerEvent(PointerEventType.Press) { titleBar.forceHitTest(false) }
        .onPointerEvent(PointerEventType.Enter) { titleBar.forceHitTest(false) }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.titleBarClient(titleBar: WindowDecorations.CustomTitleBar?): Modifier {
    if (titleBar == null) return this
    return this
        .onPointerEvent(PointerEventType.Move) { titleBar.forceHitTest(true) }
        .onPointerEvent(PointerEventType.Press) { titleBar.forceHitTest(true) }
        .onPointerEvent(PointerEventType.Enter) { titleBar.forceHitTest(true) }
}

private object WindowsDwmHelper {
    private interface DwmApi : com.sun.jna.win32.StdCallLibrary {
        fun DwmSetWindowAttribute(
            hwnd: com.sun.jna.platform.win32.WinDef.HWND,
            dwAttribute: Int,
            pvAttribute: com.sun.jna.Pointer,
            cbAttribute: Int
        ): Int

        companion object {
            val INSTANCE: DwmApi? = try {
                com.sun.jna.Native.load("dwmapi", DwmApi::class.java, com.sun.jna.win32.W32APIOptions.DEFAULT_OPTIONS)
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun applyDarkMode(window: java.awt.Window) {
        try {
            val hwnd = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getWindowPointer(window))
            val dwm = DwmApi.INSTANCE ?: return
            val darkMode = com.sun.jna.Memory(4).apply { setInt(0, 1) }
            // 20 = DWMWA_USE_IMMERSIVE_DARK_MODE (Windows 10 19041+ & Windows 11)
            dwm.DwmSetWindowAttribute(hwnd, 20, darkMode, 4)
            // 19 = fallback for older Windows 10
            dwm.DwmSetWindowAttribute(hwnd, 19, darkMode, 4)
            // 35 = DWMWA_CAPTION_COLOR (Windows 11) -> 0x000A0908 (COLORREF BGR for #08090a Void)
            val captionColor = com.sun.jna.Memory(4).apply { setInt(0, 0x000A0908) }
            dwm.DwmSetWindowAttribute(hwnd, 35, captionColor, 4)
        } catch (_: Throwable) {
            // Graceful fallback on non-Windows
        }
    }
}

private fun applyDarkAwtTheme(comp: java.awt.Component?, color: java.awt.Color) {
    if (comp == null) return
    comp.background = color
    if (comp is javax.swing.JComponent) {
        comp.isOpaque = true
    }
    if (comp is java.awt.Container) {
        for (child in comp.components) {
            applyDarkAwtTheme(child, color)
        }
    }
}

fun main() {
    System.setProperty("sun.awt.noerasebackground", "true")
    if (System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true) {
        try {
            com.sun.jna.platform.win32.Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(com.sun.jna.WString("com.echo.desktop"))
        } catch (_: Throwable) {
        }
    }
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(PlatformContext.INSTANCE, 0.10)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(System.getProperty("user.home"), ".echo/cache/coil"))
                    .maxSizeBytes(128 * 1024 * 1024L)
                    .build()
            }
            .build()
    }
    application {
        val player = remember { VlcAudioPlayer() }
        val repository = remember { DesktopMusicRepository() }
        val playbackManager = remember { DesktopPlaybackManager(player, repository) }
        val mediaControls = remember { WindowsMediaControls(player, playbackManager) }
        val discordRpc = remember {
            DesktopDiscordRpc(
                player = player,
                playbackManager = playbackManager,
                initialClientId = repository.getDiscordAppId(),
                initialEnabled = repository.isDiscordRpcEnabled(),
            )
        }
        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)

        val playerProgress = remember { Animatable(0f) }
        val springSpec = remember {
            spring<Float>(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMediumLow
            )
        }

        val onClose = {
            GoogleCastManager.disconnect(sendStop = true)
            discordRpc.close()
            player.release()
            exitApplication()
        }

        DisposableEffect(Unit) {
            val shutdownHook = Thread {
                try {
                    GoogleCastManager.disconnect(sendStop = true)
                    player.release()
                } catch (_: Throwable) {}
            }
            Runtime.getRuntime().addShutdownHook(shutdownHook)
            onDispose {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook)
                } catch (_: Throwable) {}
            }
        }

        val appScope = rememberCoroutineScope()
        val isJbrSupported = isJbrWindowDecorationsSupported

        Window(
            onCloseRequest = onClose,
            state = windowState,
            title = "Echo - YouTube Music",
            icon = painterResource("icons/icon.png"),
            undecorated = false,
            resizable = true,
            onKeyEvent = { keyEvent ->
                if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyUp && playerProgress.value > 0.05f) {
                    appScope.launch { playerProgress.animateTo(0f, springSpec) }
                    true
                } else {
                    mediaControls.handleKeyEvent(keyEvent)
                }
            },
        ) {
            LaunchedEffect(windowState.isMinimized) {
                if (windowState.isMinimized) {
                    System.gc()
                }
            }
            var customTitleBar by remember { mutableStateOf<WindowDecorations.CustomTitleBar?>(null) }
            val themeBgAwtColor = remember { java.awt.Color(0x08, 0x09, 0x0A) }

            DisposableEffect(window) {
                applyDarkAwtTheme(window, themeBgAwtColor)
                applyDarkAwtTheme(window.contentPane, themeBgAwtColor)
                applyDarkAwtTheme(window.rootPane, themeBgAwtColor)
                applyDarkAwtTheme(window.layeredPane, themeBgAwtColor)
                WindowsDwmHelper.applyDarkMode(window)

                val compListener = object : java.awt.event.ComponentAdapter() {
                    override fun componentResized(e: java.awt.event.ComponentEvent?) {
                        applyDarkAwtTheme(window, themeBgAwtColor)
                    }
                }
                window.addComponentListener(compListener)

                if (isJbrSupported) {
                    try {
                        val decorations = JBR.getWindowDecorations()
                        if (decorations != null) {
                            val tb = decorations.createCustomTitleBar()
                            tb.height = 56f
                            tb.putProperty("controls.visible", false)
                            tb.putProperty("controls.dark", true)
                            decorations.setCustomTitleBar(window, tb)
                            customTitleBar = tb
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
                onDispose {
                    window.removeComponentListener(compListener)
                    if (isJbrSupported) {
                        try {
                            JBR.getWindowDecorations()?.setCustomTitleBar(window, null)
                        } catch (_: Throwable) {}
                    }
                }
            }

        val isCurrentlyMaximized = windowState.placement == WindowPlacement.Maximized

        val triggerMinimize: () -> Unit = {
            windowState.isMinimized = true
        }

        val triggerToggleMaximize: () -> Unit = {
            windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                WindowPlacement.Floating
            } else {
                WindowPlacement.Maximized
            }
        }

        MaterialTheme(
            colorScheme = DesktopColorScheme,
            typography = DesktopTypography,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                DesktopMainView(
                    player = player,
                    repository = repository,
                    playbackManager = playbackManager,
                    discordRpc = discordRpc,
                    windowState = windowState,
                    isCurrentlyMaximized = isCurrentlyMaximized,
                    customTitleBar = customTitleBar,
                    playerProgress = playerProgress,
                    springSpec = springSpec,
                    onMinimize = triggerMinimize,
                    onToggleMaximize = triggerToggleMaximize,
                    onClose = onClose,
                )
            }
        }
    }
}
}

private data class NavState(
    val tab: Int = 0,
    val playlistId: String? = null,
    val isNowPlaying: Boolean = false,
    val isLyrics: Boolean = false,
)

@Composable
fun FrameWindowScope.DesktopMainView(
    player: VlcAudioPlayer,
    repository: DesktopMusicRepository,
    playbackManager: DesktopPlaybackManager,
    discordRpc: DesktopDiscordRpc? = null,
    windowState: WindowState,
    isCurrentlyMaximized: Boolean,
    customTitleBar: WindowDecorations.CustomTitleBar?,
    playerProgress: Animatable<Float, AnimationVector1D>,
    springSpec: AnimationSpec<Float>,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    var nav by remember { mutableStateOf(NavState(tab = 0)) }
    var backStack by remember { mutableStateOf(listOf<NavState>()) }
    var forwardStack by remember { mutableStateOf(listOf<NavState>()) }
    var searchQuery by remember { mutableStateOf("") }
    var activeSearchQuery by remember { mutableStateOf("") }

    val currentSong by playbackManager.currentSong.collectAsState()
    val scope = rememberCoroutineScope()

    val isResolving by playbackManager.isResolving.collectAsState()
    val statusMessage by playbackManager.statusMessage.collectAsState()

    var songToAddToPlaylist by remember { mutableStateOf<DesktopSong?>(null) }
    var localPlaylistsForDialog by remember { mutableStateOf<List<DesktopPlaylist>>(emptyList()) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var availableUpdate by remember { mutableStateOf<DesktopReleaseInfo?>(null) }
    var isCheckingForUpdate by remember { mutableStateOf(false) }
    var showConnectFlyout by remember { mutableStateOf(false) }
    val selectedDeviceId by DeviceDiscoveryService.selectedDeviceId.collectAsState()

    LaunchedEffect(Unit) {
        if (!UpdaterPreferences.autoCheck) return@LaunchedEffect
        try {
            val result = DesktopUpdater.checkForUpdate()
            val update = result.getOrNull()
            if (update != null) {
                if (update.tagName != UpdaterPreferences.dismissedTag) {
                    availableUpdate = update
                    // Unduh otomatis di latar belakang
                    DesktopUpdater.downloadAndStageUpdate(update)
                    if (DesktopUpdater.isUpdateStaged.value && UpdaterPreferences.notifications) {
                        DesktopUpdater.showDesktopNotification(
                            title = "Pembaruan Siap Dipasang!",
                            message = "Versi ${update.versionName} siap dipasang. Klik untuk me-restart Echo.",
                            onNotificationClick = { DesktopUpdater.applyUpdateAndRestart() }
                        )
                    }
                }
            }
        } catch (_: Throwable) {
            // Ignore silent update errors
        }
    }

    fun navigateTo(next: NavState) {
        if (next == nav) return
        backStack = backStack + nav
        forwardStack = emptyList()
        nav = next
    }

    fun goBack() {
        if (backStack.isNotEmpty()) {
            val prev = backStack.last()
            backStack = backStack.dropLast(1)
            forwardStack = listOf(nav) + forwardStack
            nav = prev
        }
    }

    fun goForward() {
        if (forwardStack.isNotEmpty()) {
            val next = forwardStack.first()
            forwardStack = forwardStack.drop(1)
            backStack = backStack + nav
            nav = next
        }
    }

    LaunchedEffect(songToAddToPlaylist) {
        if (songToAddToPlaylist != null) {
            localPlaylistsForDialog = repository.getLocalPlaylists()
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(3500)
            toastMessage = null
        }
    }

    if (songToAddToPlaylist != null) {
        AddToPlaylistDialog(
            song = songToAddToPlaylist!!,
            playlists = localPlaylistsForDialog,
            onDismissRequest = { songToAddToPlaylist = null },
            onSelectPlaylist = { pl ->
                scope.launch {
                    repository.addSongToLocalPlaylist(pl.id, songToAddToPlaylist!!)
                    localPlaylistsForDialog = repository.getLocalPlaylists()
                    toastMessage = "Lagu ditambahkan ke '${pl.title}'"
                    songToAddToPlaylist = null
                }
            },
            onCreateNewPlaylistAndAdd = { title ->
                scope.launch {
                    repository.createLocalPlaylist(title, initialSongs = listOf(songToAddToPlaylist!!))
                    localPlaylistsForDialog = repository.getLocalPlaylists()
                    toastMessage = "Playlist '$title' dibuat dan lagu ditambahkan!"
                    songToAddToPlaylist = null
                }
            }
        )
    }

    if (availableUpdate != null) {
        UpdateDialog(
            releaseInfo = availableUpdate!!,
            onDismissRequest = { availableUpdate = null }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(DesktopColors.Background)
            .onPreviewKeyEvent {
                if (it.key == Key.Escape) {
                    if (showConnectFlyout) {
                        showConnectFlyout = false
                        true
                    } else if (playerProgress.value > 0.05f) {
                        scope.launch { playerProgress.animateTo(0f, springSpec) }
                        true
                    } else if (nav.isLyrics) {
                        navigateTo(nav.copy(isLyrics = false))
                        true
                    } else false
                } else false
            }
    ) {
        val isCompactRail by remember { derivedStateOf { maxWidth < 900.dp } }
        val railWidth = if (isCompactRail) 72.dp else 84.dp

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. Top Navbar (Spotify Desktop Style)
                this@DesktopMainView.DesktopTopBar(
                    canGoBack = backStack.isNotEmpty(),
                    canGoForward = forwardStack.isNotEmpty(),
                    onBack = ::goBack,
                    onForward = ::goForward,
                    isHomeSelected = nav.tab == 0 && !nav.isLyrics && playerProgress.value < 0.5f,
                    onHome = {
                        if (playerProgress.value > 0.05f) {
                            scope.launch { playerProgress.animateTo(0f, springSpec) }
                        }
                        navigateTo(NavState(tab = 0))
                    },
                    searchQuery = searchQuery,
                    onSearchQueryChange = {
                        searchQuery = it
                        if (it.isBlank()) {
                            activeSearchQuery = ""
                        }
                    },
                    onSearchSubmit = {
                        if (searchQuery.isNotBlank()) {
                            activeSearchQuery = searchQuery.trim()
                            if (playerProgress.value > 0.05f) {
                                scope.launch { playerProgress.animateTo(0f, springSpec) }
                            }
                            if (nav.tab != 0 || nav.isLyrics) {
                                navigateTo(NavState(tab = 0))
                            }
                        }
                    },
                    onClearSearch = {
                        searchQuery = ""
                        activeSearchQuery = ""
                    },
                    windowState = windowState,
                    isMaximized = isCurrentlyMaximized,
                    customTitleBar = customTitleBar,
                    onMinimize = onMinimize,
                    onToggleMaximize = onToggleMaximize,
                    onClose = onClose,
                )

                // 2. Body Area: Left Sidebar + Central Content Card
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NavigationRail(
                        containerColor = DesktopColors.Carbon,
                        modifier = Modifier
                            .width(railWidth)
                            .fillMaxHeight()
                            .clip(DesktopRadii.Card)
                            .border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Card),
                    ) {
                        Spacer(modifier = Modifier.height(14.dp))

                        NavigationRailItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Beranda") },
                            label = { Text("Beranda", fontSize = 11.sp, fontWeight = if (nav.tab == 0 && !nav.isLyrics && playerProgress.value < 0.5f) FontWeight.Medium else FontWeight.Normal) },
                            selected = nav.tab == 0 && !nav.isLyrics && playerProgress.value < 0.5f,
                            onClick = {
                                if (playerProgress.value > 0.05f) {
                                    scope.launch { playerProgress.animateTo(0f, springSpec) }
                                }
                                navigateTo(NavState(tab = 0))
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = DesktopColors.Void,
                                unselectedIconColor = DesktopColors.Fog,
                                selectedTextColor = DesktopColors.Paper,
                                unselectedTextColor = DesktopColors.Fog,
                                indicatorColor = DesktopColors.AcidLime,
                            ),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        NavigationRailItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Koleksi Playlist") },
                            label = { Text("Playlist", fontSize = 11.sp, fontWeight = if (nav.tab == 1 && !nav.isLyrics && playerProgress.value < 0.5f) FontWeight.Medium else FontWeight.Normal) },
                            selected = nav.tab == 1 && !nav.isLyrics && playerProgress.value < 0.5f,
                            onClick = {
                                if (playerProgress.value > 0.05f) {
                                    scope.launch { playerProgress.animateTo(0f, springSpec) }
                                }
                                navigateTo(NavState(tab = 1))
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = DesktopColors.Void,
                                unselectedIconColor = DesktopColors.Fog,
                                selectedTextColor = DesktopColors.Paper,
                                unselectedTextColor = DesktopColors.Fog,
                                indicatorColor = DesktopColors.AcidLime,
                            ),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        NavigationRailItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                            label = { Text("Pengaturan", fontSize = 11.sp, fontWeight = if (nav.tab == 2 && !nav.isLyrics && playerProgress.value < 0.5f) FontWeight.Medium else FontWeight.Normal) },
                            selected = nav.tab == 2 && !nav.isLyrics && playerProgress.value < 0.5f,
                            onClick = {
                                if (playerProgress.value > 0.05f) {
                                    scope.launch { playerProgress.animateTo(0f, springSpec) }
                                }
                                navigateTo(NavState(tab = 2))
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = DesktopColors.Void,
                                unselectedIconColor = DesktopColors.Fog,
                                selectedTextColor = DesktopColors.Paper,
                                unselectedTextColor = DesktopColors.Fog,
                                indicatorColor = DesktopColors.AcidLime,
                            ),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        )
                    }

                    // Konten Utama Tengah (Rounded Container Card - Linear 12px & Hairline Border)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(DesktopRadii.Card)
                            .border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Card)
                            .background(DesktopColors.Carbon)
                    ) {
                        if (nav.isLyrics && currentSong != null) {
                            DesktopLyricsPageView(
                                song = currentSong!!,
                                player = player,
                                repository = repository,
                                playbackManager = playbackManager,
                                onClose = { navigateTo(nav.copy(isLyrics = false)) },
                                onAddToPlaylist = { songToAddToPlaylist = it }
                            )
                        } else {
                            when (nav.tab) {
                                0 -> HomeView(
                                    repository = repository,
                                    playbackManager = playbackManager,
                                    activeSearchQuery = activeSearchQuery,
                                    onClearSearch = {
                                        searchQuery = ""
                                        activeSearchQuery = ""
                                    },
                                    onSongSelected = { song, list -> playbackManager.playSong(song, list) },
                                    onAddToPlaylist = { songToAddToPlaylist = it }
                                )
                                1 -> PlaylistView(
                                    repository = repository,
                                    playbackManager = playbackManager,
                                    activePlaylistId = nav.playlistId,
                                    onOpenPlaylist = { playlistId ->
                                        navigateTo(NavState(tab = 1, playlistId = playlistId))
                                    },
                                    onBack = { goBack() },
                                    onAddToPlaylistRequest = { songToAddToPlaylist = it }
                                )
                                2 -> SettingsView(
                                    player = player,
                                    discordRpc = discordRpc,
                                    onToggleDiscordRpc = { enabled ->
                                        repository.setDiscordRpcEnabled(enabled)
                                    },
                                    isCheckingForUpdate = isCheckingForUpdate,
                                    onCheckForUpdate = {
                                        scope.launch {
                                            if (isCheckingForUpdate) return@launch
                                            isCheckingForUpdate = true
                                            try {
                                                val result = DesktopUpdater.checkForUpdate(forceRefresh = true)
                                                val update = result.getOrNull()
                                                if (update != null) {
                                                    availableUpdate = update
                                                } else if (result.isSuccess) {
                                                    toastMessage = "Echo sudah versi terbaru (${DesktopUpdater.CURRENT_VERSION})"
                                                } else {
                                                    toastMessage = "Gagal memeriksa pembaruan: ${result.exceptionOrNull()?.message ?: "Koneksi bermasalah"}"
                                                }
                                            } catch (e: Throwable) {
                                                toastMessage = "Gagal memeriksa pembaruan: ${e.message}"
                                            } finally {
                                                isCheckingForUpdate = false
                                            }
                                        }
                                    },
                                    onOpenConnectFlyout = { showConnectFlyout = true },
                                )
                            }
                        }

                        // Status / Toast overlay
                        val activeMessage = toastMessage ?: statusMessage
                        if (activeMessage != null || isResolving) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DesktopColors.CardElevated),
                                border = BorderStroke(1.dp, DesktopColors.Border),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isResolving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = DesktopColors.Primary,
                                            strokeWidth = 2.5.dp,
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    } else {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = DesktopColors.AcidLime,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }
                                    Text(
                                        text = activeMessage ?: "Menghubungkan ke YouTube Music...",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }

                // Ruang bawah untuk memastikan scroll list tidak tertutup mini player
                Spacer(modifier = Modifier.height(76.dp))
            }

            // 3. Unified Player Layer (Mini Player <-> Fullscreen Player dengan transisi spring)
            if (currentSong != null) {
                UnifiedPlayerLayer(
                    player = player,
                    repository = repository,
                    playbackManager = playbackManager,
                    progress = playerProgress,
                    springSpec = springSpec,
                    onClose = {
                        scope.launch { playerProgress.animateTo(0f, springSpec) }
                    },
                    onOpenLyrics = {
                        scope.launch { playerProgress.animateTo(0f, springSpec) }
                        navigateTo(nav.copy(isLyrics = true))
                    },
                    onAddToPlaylist = { songToAddToPlaylist = it },
                    isConnectFlyoutOpen = showConnectFlyout,
                    selectedDeviceId = selectedDeviceId,
                    onToggleConnectFlyout = { showConnectFlyout = !showConnectFlyout },
                )
            }

            // In-App Connect Device Flyout (Spotify Style)
            if (showConnectFlyout) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            showConnectFlyout = false
                        }
                ) {
                    ConnectDeviceFlyout(
                        onClose = { showConnectFlyout = false },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 84.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FrameWindowScope.DesktopTopBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    isHomeSelected: Boolean,
    onHome: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onClearSearch: () -> Unit,
    windowState: WindowState,
    isMaximized: Boolean,
    customTitleBar: WindowDecorations.CustomTitleBar?,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val searchWidth = (maxWidth * 0.36f).coerceIn(240.dp, 440.dp)

            // Draggable background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .titleBarDrag(customTitleBar)
            )

            // Left: Navigation Buttons (< and >)
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .titleBarClient(customTitleBar)
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = if (canGoBack) Color.White else DesktopColors.TextTertiary.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = onForward,
                    enabled = canGoForward,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Maju",
                        tint = if (canGoForward) Color.White else DesktopColors.TextTertiary.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Center: Circular Home Button + Pill Search Bar
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .titleBarClient(customTitleBar),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onHome,
                    shape = DesktopRadii.Button,
                    color = if (isHomeSelected) DesktopColors.Obsidian else Color.Transparent,
                    border = BorderStroke(1.dp, if (isHomeSelected) DesktopColors.Border else Color.Transparent),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Beranda",
                            tint = if (isHomeSelected) DesktopColors.AcidLime else DesktopColors.Fog,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = DesktopRadii.Input,
                    color = DesktopColors.Obsidian,
                    border = BorderStroke(1.dp, if (searchQuery.isNotEmpty()) DesktopColors.Smoke else DesktopColors.Border),
                    modifier = Modifier
                        .width(searchWidth)
                        .height(36.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = if (searchQuery.isNotEmpty()) DesktopColors.AcidLime else DesktopColors.Fog,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val searchTextStyle = TextStyle(
                            color = DesktopColors.Paper,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                singleLine = true,
                                textStyle = searchTextStyle,
                                cursorBrush = SolidColor(DesktopColors.AcidLime),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onKeyEvent {
                                        if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                                            onSearchSubmit()
                                            true
                                        } else false
                                    },
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                "Cari lagu, artis, atau album...",
                                                style = searchTextStyle.copy(color = DesktopColors.Fog),
                                                maxLines = 1,
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = onClearSearch,
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Hapus",
                                    tint = DesktopColors.Fog,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Right: Window Control Buttons (Minimize, Maximize / Restore, Close)
            if (customTitleBar != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .titleBarClient(customTitleBar)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                WindowControlButton(
                    onClick = onMinimize,
                    contentDescription = "Minimalkan",
                ) { iconColor ->
                    Canvas(modifier = Modifier.size(10.dp)) {
                        val stroke = 1.dp.toPx()
                        val y = size.height / 2
                        drawLine(
                            color = iconColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = stroke
                        )
                    }
                }

                WindowControlButton(
                    onClick = onToggleMaximize,
                    contentDescription = if (isMaximized) "Pulihkan" else "Maksimalkan",
                ) { iconColor ->
                    Canvas(modifier = Modifier.size(10.dp)) {
                        val stroke = 1.dp.toPx()
                        if (isMaximized) {
                            val s = 8.dp.toPx()
                            val d = 2.dp.toPx()

                            // Back rectangle (top and right edges)
                            drawLine(
                                color = iconColor,
                                start = Offset(d, stroke / 2),
                                end = Offset(d + s - stroke / 2, stroke / 2),
                                strokeWidth = stroke
                            )
                            drawLine(
                                color = iconColor,
                                start = Offset(d + s - stroke / 2, stroke / 2),
                                end = Offset(d + s - stroke / 2, d + s - stroke / 2),
                                strokeWidth = stroke
                            )
                            drawLine(
                                color = iconColor,
                                start = Offset(s, d + s - stroke / 2),
                                end = Offset(d + s - stroke / 2, d + s - stroke / 2),
                                strokeWidth = stroke
                            )
                            drawLine(
                                color = iconColor,
                                start = Offset(d, stroke / 2),
                                end = Offset(d, d),
                                strokeWidth = stroke
                            )

                            // Front rectangle
                            drawRect(
                                color = iconColor,
                                topLeft = Offset(stroke / 2, d + stroke / 2),
                                size = Size(s - stroke, s - stroke),
                                style = Stroke(width = stroke)
                            )
                        } else {
                            drawRect(
                                color = iconColor,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke)
                            )
                        }
                    }
                }

                WindowControlButton(
                    onClick = onClose,
                    isClose = true,
                    contentDescription = "Tutup",
                ) { iconColor ->
                    Canvas(modifier = Modifier.size(10.dp)) {
                        val stroke = 1.dp.toPx()
                        drawLine(
                            color = iconColor,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = stroke
                        )
                        drawLine(
                            color = iconColor,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, 0f),
                            strokeWidth = stroke
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun WindowControlButton(
    onClick: () -> Unit,
    isClose: Boolean = false,
    contentDescription: String,
    content: @Composable (iconColor: Color) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgColor = when {
        isClose && isPressed -> Color(0xFFC42B1C)
        isClose && isHovered -> Color(0xFFE81123)
        !isClose && isPressed -> Color.White.copy(alpha = 0.16f)
        !isClose && isHovered -> Color.White.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    val iconColor = when {
        isClose && (isHovered || isPressed) -> Color.White
        else -> Color(0xFFCCCCCC)
    }

    Box(
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onClickLabel = contentDescription
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(iconColor)
    }
}

@Composable
fun HomeView(
    repository: DesktopMusicRepository,
    playbackManager: DesktopPlaybackManager,
    activeSearchQuery: String,
    onClearSearch: () -> Unit,
    onSongSelected: (DesktopSong, List<DesktopSong>) -> Unit,
    onAddToPlaylist: (DesktopSong) -> Unit,
) {
    val currentSong by playbackManager.currentSong.collectAsState()
    val isPlaying by playbackManager.player.isPlaying.collectAsState()
    val downloadedIds by repository.downloadManager.downloadedSongIds.collectAsState()
    val downloadProgress by repository.downloadManager.activeDownloadProgress.collectAsState()

    var quickPicks by remember { mutableStateOf<List<DesktopSong>>(emptyList()) }
    var isLoadingHome by remember { mutableStateOf(true) }
    var isLoadingMoreSongs by remember { mutableStateOf(false) }
    var songsPage by remember { mutableStateOf(1) }
    var canLoadMoreSongs by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf("Semua") }
    val categories = listOf("Semua", "Trending", "Pop Indo", "Barat / Global", "Santai & Akustik", "Lagu Galau")

    var searchResults by remember { mutableStateOf<List<DesktopSong>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val homeListState = rememberLazyListState()

    fun loadHome(cat: String = selectedCategory) {
        scope.launch {
            isLoadingHome = true
            songsPage = 1
            canLoadMoreSongs = true
            quickPicks = repository.getHomeQuickPicks(cat)
            isLoadingHome = false
        }
    }

    val shouldLoadMoreSongs by remember {
        derivedStateOf {
            val layoutInfo = homeListState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMoreSongs) {
        if (shouldLoadMoreSongs && !isLoadingHome && !isLoadingMoreSongs && canLoadMoreSongs && activeSearchQuery.isEmpty()) {
            isLoadingMoreSongs = true
            val nextPage = songsPage + 1
            val newSongs = repository.getMoreHomeQuickPicks(selectedCategory, page = nextPage)
            if (newSongs.isNotEmpty()) {
                val existingIds = quickPicks.map { it.id }.toHashSet()
                val uniqueNew = newSongs.filter { !existingIds.contains(it.id) }
                if (uniqueNew.isNotEmpty()) {
                    // RAM Safety: batasi list di memori maksimal 200 lagu agar penggunaan heap tetap hemat
                    quickPicks = (quickPicks + uniqueNew).take(200)
                    songsPage = nextPage
                } else {
                    songsPage = nextPage
                }
            } else {
                canLoadMoreSongs = false
            }
            isLoadingMoreSongs = false
        }
    }

    fun resetSearch() = onClearSearch()

    LaunchedEffect(Unit) {
        loadHome()
    }

    LaunchedEffect(activeSearchQuery) {
        if (activeSearchQuery.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        isSearching = true
        searchResults = repository.searchSongs(activeSearchQuery)
        isSearching = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(28.dp)) {
        if (activeSearchQuery.isNotEmpty()) {
            // Tampilan Hasil Pencarian
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Hasil Pencarian",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (searchResults.isNotEmpty()) "${searchResults.size} lagu ditemukan untuk \"$activeSearchQuery\"" else "Menampilkan pencarian untuk \"$activeSearchQuery\"",
                        fontSize = 13.sp,
                        color = DesktopColors.TextSecondary,
                    )
                }
                TextButton(
                    onClick = { resetSearch() },
                    colors = ButtonDefaults.textButtonColors(contentColor = DesktopColors.Primary),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Kembali ke Rekomendasi",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isSearching) {
                SongListSkeleton(count = 6)
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DesktopColors.Card),
                        border = BorderStroke(1.dp, DesktopColors.Border),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = DesktopColors.TextTertiary,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "Lagu Tidak Ditemukan",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Tidak ditemukan lagu untuk kata kunci \"$activeSearchQuery\". Periksa kembali ejaan atau gunakan istilah lain.",
                                color = DesktopColors.TextSecondary,
                                fontSize = 13.sp,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { resetSearch() },
                                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.CardElevated),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Kembali ke Rekomendasi", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items = searchResults, key = { it.id }, contentType = { "search_song" }) { song ->
                        val isThisSongPlaying = currentSong?.id == song.id && isPlaying
                        val isDownloaded = downloadedIds.contains(song.id)
                        val isDownloading = downloadProgress.containsKey(song.id)

                        SongListItem(
                            song = song,
                            onPlay = { onSongSelected(song, searchResults) },
                            onAddToPlaylist = { onAddToPlaylist(song) },
                            isPlaying = isThisSongPlaying,
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            onToggleDownload = {
                                scope.launch {
                                    if (isDownloaded) {
                                        repository.downloadManager.deleteDownload(song.id)
                                    } else {
                                        repository.downloadManager.downloadSong(song, repository)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Tampilan Beranda & Rekomendasi Utama
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Lagu Populer & Rekomendasi",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (quickPicks.isNotEmpty()) "${quickPicks.size} lagu siap diputar • Rekomendasi kurasi YouTube Music" else "Mengambil rekomendasi musik terpopuler...",
                        fontSize = 13.sp,
                        color = DesktopColors.TextSecondary,
                    )
                }
                IconButton(
                    onClick = { loadHome(selectedCategory) },
                    enabled = !isLoadingHome,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Muat Ulang Rekomendasi",
                        tint = if (!isLoadingHome) Color.White else DesktopColors.TextTertiary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Filter Kategori / Mood Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { cat ->
                    val isSelected = cat == selectedCategory
                    val chipBg = if (isSelected) DesktopColors.AcidLime else DesktopColors.Obsidian
                    val chipTextColor = if (isSelected) DesktopColors.Void else DesktopColors.Mist
                    val chipBorder = if (isSelected) null else BorderStroke(1.dp, DesktopColors.Border)
                    Box(
                        modifier = Modifier
                            .clip(DesktopRadii.Pill)
                            .then(if (chipBorder != null) Modifier.border(chipBorder, DesktopRadii.Pill) else Modifier)
                            .background(chipBg)
                            .clickable {
                                if (selectedCategory != cat) {
                                    selectedCategory = cat
                                    loadHome(cat)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat,
                            color = chipTextColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))

            if (isLoadingHome) {
                SongListSkeleton(count = 7)
            } else if (quickPicks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
                        border = BorderStroke(1.dp, DesktopColors.Border),
                        shape = DesktopRadii.Card,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Default.MusicOff,
                                contentDescription = null,
                                tint = DesktopColors.Fog,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Tidak Dapat Memuat Rekomendasi",
                                color = DesktopColors.Paper,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Pastikan komputer Anda terhubung ke internet untuk memuat lagu.",
                                color = DesktopColors.Fog,
                                fontSize = 13.sp,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { loadHome() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DesktopColors.AcidLime,
                                    contentColor = DesktopColors.Void,
                                ),
                                shape = DesktopRadii.Button,
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = DesktopColors.Void, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Muat Ulang Rekomendasi", color = DesktopColors.Void, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = homeListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = quickPicks,
                        key = { it.id },
                        contentType = { "home_song" }
                    ) { song ->
                        val isThisSongPlaying = currentSong?.id == song.id && isPlaying
                        val isDownloaded = downloadedIds.contains(song.id)
                        val isDownloading = downloadProgress.containsKey(song.id)

                        SongListItem(
                            song = song,
                            onPlay = { onSongSelected(song, quickPicks) },
                            onAddToPlaylist = { onAddToPlaylist(song) },
                            isPlaying = isThisSongPlaying,
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            onToggleDownload = {
                                scope.launch {
                                    if (isDownloaded) {
                                        repository.downloadManager.deleteDownload(song.id)
                                    } else {
                                        repository.downloadManager.downloadSong(song, repository)
                                    }
                                }
                            }
                        )
                    }

                    if (isLoadingMoreSongs) {
                        item(key = "loading_more_songs_indicator") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = DesktopColors.AcidLime,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "Memuat rekomendasi lagu berikutnya...",
                                        color = DesktopColors.Fog,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongListItem(
    song: DesktopSong,
    onPlay: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    onToggleDownload: (() -> Unit)? = null,
) {
    val borderColor = if (isPlaying) DesktopColors.AcidLime.copy(alpha = 0.5f) else DesktopColors.Border
    val cardColor = if (isPlaying) DesktopColors.Obsidian else DesktopColors.Carbon
    Card(
        shape = DesktopRadii.Button,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(50.dp)) {
                if (!song.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(DesktopRadii.Badge)
                            .background(DesktopColors.Obsidian),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(DesktopRadii.Badge)
                            .background(DesktopColors.Obsidian),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = DesktopColors.Fog, modifier = Modifier.size(24.dp))
                    }
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(DesktopRadii.Badge)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayingEqualizerBars(
                            isPlaying = true,
                            barColor = DesktopColors.AcidLime,
                            maxHeight = 20.dp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontWeight = if (isPlaying) FontWeight.Medium else FontWeight.Normal,
                    fontSize = 14.5.sp,
                    color = if (isPlaying) DesktopColors.AcidLime else DesktopColors.Paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDownloaded) {
                        Box(
                            modifier = Modifier
                                .clip(DesktopRadii.Badge)
                                .background(DesktopColors.Slate.copy(alpha = 0.5f))
                                .border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Badge)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("OFFLINE", fontSize = 9.sp, color = DesktopColors.Mist, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = song.artist,
                        fontSize = 12.5.sp,
                        color = DesktopColors.Fog,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Text(
                text = song.durationText,
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = DesktopColors.Fog,
                modifier = Modifier.padding(horizontal = 14.dp),
            )

            if (onToggleDownload != null) {
                IconButton(
                    onClick = onToggleDownload,
                    enabled = !isDownloading,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = DesktopColors.Obsidian),
                    modifier = Modifier.size(34.dp).clip(DesktopRadii.Button).border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Button),
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = DesktopColors.AcidLime,
                            strokeWidth = 2.dp,
                        )
                    } else if (isDownloaded) {
                        Icon(Icons.Default.DownloadDone, contentDescription = "Tersimpan Offline (Klik untuk Hapus)", tint = DesktopColors.AcidLime, modifier = Modifier.size(17.dp))
                    } else {
                        Icon(Icons.Default.Download, contentDescription = "Simpan Offline", tint = DesktopColors.Fog, modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (onAddToPlaylist != null) {
                IconButton(
                    onClick = onAddToPlaylist,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = DesktopColors.Obsidian),
                    modifier = Modifier.size(34.dp).clip(DesktopRadii.Button).border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Button),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Tambahkan ke Playlist",
                        tint = DesktopColors.Fog,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            IconButton(
                onClick = onPlay,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isPlaying) DesktopColors.AcidLime else DesktopColors.Obsidian,
                    contentColor = if (isPlaying) DesktopColors.Void else DesktopColors.Paper,
                ),
                modifier = Modifier
                    .size(34.dp)
                    .clip(DesktopRadii.Button)
                    .border(BorderStroke(1.dp, if (isPlaying) DesktopColors.AcidLime else DesktopColors.Border), DesktopRadii.Button),
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Jeda Lagu" else "Putar Lagu",
                    tint = if (isPlaying) DesktopColors.Void else DesktopColors.Paper,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsView(
    player: VlcAudioPlayer,
    discordRpc: DesktopDiscordRpc? = null,
    onToggleDiscordRpc: ((Boolean) -> Unit)? = null,
    isCheckingForUpdate: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
    onOpenConnectFlyout: (() -> Unit)? = null,
) {
    val isDiscordEnabled by discordRpc?.isEnabled?.collectAsState() ?: remember { mutableStateOf(false) }
    var autoCheckEnabled by remember { mutableStateOf(UpdaterPreferences.autoCheck) }
    var notificationsEnabled by remember { mutableStateOf(UpdaterPreferences.notifications) }
    val isUpdateStaged by DesktopUpdater.isUpdateStaged.collectAsState()
    val isDownloadingUpdate by DesktopUpdater.isDownloadingUpdate.collectAsState()
    val updateProgress by DesktopUpdater.updateDownloadProgress.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState())) {
        Text("Pengaturan & Informasi Sistem", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = DesktopColors.Paper)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Integrasi Discord, panduan pintasan keyboard, dan informasi sistem", fontSize = 13.sp, color = DesktopColors.Fog)
        Spacer(modifier = Modifier.height(20.dp))

        // Card Discord Rich Presence
        Card(
            shape = DesktopRadii.Card,
            colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
            border = BorderStroke(1.dp, DesktopColors.Border),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Discord Rich Presence", fontWeight = FontWeight.Medium, color = DesktopColors.Paper, fontSize = 16.sp)
                    }

                    Switch(
                        checked = isDiscordEnabled,
                        onCheckedChange = { checked ->
                            discordRpc?.setEnabled(checked)
                            onToggleDiscordRpc?.invoke(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DesktopColors.Void,
                            checkedTrackColor = DesktopColors.AcidLime,
                            uncheckedThumbColor = DesktopColors.Fog,
                            uncheckedTrackColor = DesktopColors.Obsidian,
                        )
                    )
                }
            }
        }

        // Card Transmisi Nirkabel & Cast to TV
        Card(
            shape = DesktopRadii.Card,
            colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
            border = BorderStroke(1.dp, DesktopColors.Border),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Transmisi Nirkabel & Cast to TV", fontWeight = FontWeight.Medium, color = DesktopColors.Paper, fontSize = 16.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (onOpenConnectFlyout != null) {
                                    onOpenConnectFlyout()
                                } else {
                                    WindowsCastHelper.openCastFlyout()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesktopColors.AcidLime,
                                contentColor = DesktopColors.Void,
                            ),
                            shape = DesktopRadii.Button,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                Icons.Default.Cast,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = DesktopColors.Void,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Pindai Smart TV",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DesktopColors.Void,
                            )
                        }
                    }
                }
            }
        }

        // Card Keyboard Shortcuts
        Card(
            shape = DesktopRadii.Card,
            colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
            border = BorderStroke(1.dp, DesktopColors.Border),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Pintasan Keyboard & Windows SMTC", fontWeight = FontWeight.Medium, color = DesktopColors.Paper, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(10.dp))
                val shortcuts = listOf(
                    "Spasi" to "Play / Pause pemutaran musik",
                    "Media Play/Pause/Next/Prev" to "Tombol fisik multimedia pada keyboard Windows",
                    "Ctrl + Alt + Panah Kanan / Kiri" to "Pindah ke lagu berikutnya / sebelumnya dalam antrean",
                    "Ctrl + Panah Kanan / Kiri" to "Maju / Mundur 5 detik (Seek slider)",
                    "Ctrl + Panah Atas / Bawah" to "Menaikkan / Menurunkan volume audio pemutar",
                    "Win + K" to "Buka panel transmisi layar Windows (Smart TV & Cast)",
                )
                shortcuts.forEach { (key, desc) ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(DesktopRadii.Badge)
                                .background(DesktopColors.Obsidian)
                                .border(BorderStroke(1.dp, DesktopColors.Border), DesktopRadii.Badge)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = key,
                                fontSize = 11.sp,
                                color = DesktopColors.AcidLime,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(desc, color = DesktopColors.Mist, fontSize = 12.5.sp)
                    }
                }
            }
        }

        // Card Info Metrolist
        Card(
            shape = DesktopRadii.Card,
            colors = CardDefaults.cardColors(containerColor = DesktopColors.Carbon),
            border = BorderStroke(1.dp, DesktopColors.Border),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Tentang Echo", fontWeight = FontWeight.Medium, color = DesktopColors.Paper, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Versi ${DesktopUpdater.CURRENT_VERSION}",
                            color = DesktopColors.Fog,
                            fontSize = 12.5.sp,
                        )
                        if (isUpdateStaged) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Pembaruan siap dipasang!",
                                color = DesktopColors.AcidLime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        } else if (isDownloadingUpdate) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Mengunduh pembaruan di latar belakang (${(updateProgress * 100).toInt()}%)...",
                                color = DesktopColors.Mist,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    if (isUpdateStaged) {
                        Button(
                            onClick = { DesktopUpdater.applyUpdateAndRestart() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesktopColors.AcidLime,
                                contentColor = DesktopColors.Void,
                            ),
                            shape = DesktopRadii.Badge,
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = DesktopColors.Void,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Restart Echo untuk Memperbarui",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DesktopColors.Void,
                            )
                        }
                    } else if (isDownloadingUpdate) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                progress = { updateProgress },
                                modifier = Modifier.size(16.dp),
                                color = DesktopColors.AcidLime,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${(updateProgress * 100).toInt()}%", fontSize = 12.sp, color = DesktopColors.AcidLime)
                        }
                    } else {
                        Button(
                            onClick = onCheckForUpdate,
                            enabled = !isCheckingForUpdate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DesktopColors.AcidLime,
                                contentColor = DesktopColors.Void,
                                disabledContainerColor = DesktopColors.Obsidian,
                                disabledContentColor = DesktopColors.Fog,
                            ),
                            shape = DesktopRadii.Badge,
                        ) {
                            if (isCheckingForUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = DesktopColors.Fog,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Memeriksa...", fontSize = 12.sp, color = DesktopColors.Fog)
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = DesktopColors.Void,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Periksa Pembaruan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DesktopColors.Void,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = DesktopColors.Border,
                )

                // Saklar 1: Cek otomatis
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            "Cek pembaruan otomatis saat start",
                            color = DesktopColors.Paper,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Memeriksa versi baru Echo secara otomatis saat aplikasi dibuka",
                            color = DesktopColors.Fog,
                            fontSize = 12.sp,
                        )
                    }

                    Switch(
                        checked = autoCheckEnabled,
                        onCheckedChange = { checked ->
                            autoCheckEnabled = checked
                            UpdaterPreferences.autoCheck = checked
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DesktopColors.Void,
                            checkedTrackColor = DesktopColors.AcidLime,
                            uncheckedThumbColor = DesktopColors.Fog,
                            uncheckedTrackColor = DesktopColors.Obsidian,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Saklar 2: Notifikasi update
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            "Notifikasi pembaruan",
                            color = if (autoCheckEnabled) DesktopColors.Paper else DesktopColors.Fog,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Menampilkan notifikasi sistem saat pembaruan tersedia",
                            color = DesktopColors.Fog,
                            fontSize = 12.sp,
                        )
                    }

                    Switch(
                        checked = notificationsEnabled && autoCheckEnabled,
                        enabled = autoCheckEnabled,
                        onCheckedChange = { checked ->
                            notificationsEnabled = checked
                            UpdaterPreferences.notifications = checked
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DesktopColors.Void,
                            checkedTrackColor = DesktopColors.AcidLime,
                            uncheckedThumbColor = DesktopColors.Fog,
                            uncheckedTrackColor = DesktopColors.Obsidian,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SpotifyStyleProgressBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = DesktopColors.Paper,
    inactiveColor: Color = DesktopColors.Slate,
    hoverColor: Color = DesktopColors.AcidLime,
    trackHeight: androidx.compose.ui.unit.Dp = 3.dp,
    thumbSize: androidx.compose.ui.unit.Dp = 8.dp,
) {
    var isHovered by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val hoverAnim by animateFloatAsState(
        targetValue = if (isHovered || isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "ProgressBarHoverAnim"
    )

    val currentFraction = (if (isDragging) dragFraction else value).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(16.dp)
            .pointerHoverIcon(PointerIcon.Hand)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { if (!isDragging) isHovered = false }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(fraction)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(dragFraction)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChange(dragFraction)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val newFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragFraction = newFraction
                        onValueChange(newFraction)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
            val totalWidth = size.width
            val totalHeight = size.height
            val trackH = trackHeight.toPx()
            val thumbR = (thumbSize.toPx() / 2f) * hoverAnim
            val trackY = (totalHeight - trackH) / 2f

            // 1. Inactive Track
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, trackY),
                size = Size(totalWidth, trackH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f, trackH / 2f)
            )

            // 2. Active Track
            val activeW = (totalWidth * currentFraction).coerceIn(0f, totalWidth)
            val currentActiveColor = if (hoverAnim > 0.01f) {
                androidx.compose.ui.graphics.lerp(activeColor, hoverColor, hoverAnim)
            } else {
                activeColor
            }

            if (activeW > 0f) {
                drawRoundRect(
                    color = currentActiveColor,
                    topLeft = Offset(0f, trackY),
                    size = Size(activeW, trackH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f, trackH / 2f)
                )
            }

            // 3. Thumb Indicator
            if (hoverAnim > 0.01f && thumbR > 0.5f) {
                val thumbCenterX = (totalWidth * currentFraction).coerceIn(thumbR, totalWidth - thumbR)
                val thumbCenterY = totalHeight / 2f
                drawCircle(
                    color = DesktopColors.AcidLime,
                    radius = thumbR,
                    center = Offset(thumbCenterX, thumbCenterY)
                )
            }
        }
    }
}

@Composable
private fun PlayerSeekBarRow(
    player: VlcAudioPlayer,
    playbackManager: DesktopPlaybackManager,
    modifier: Modifier = Modifier,
) {
    val currentPosition by player.currentPosition.collectAsState()
    val duration by player.duration.collectAsState()

    Row(
        modifier = modifier.height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatTime(currentPosition),
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = DesktopColors.Fog,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
        )
        Spacer(modifier = Modifier.width(8.dp))
        SpotifyStyleProgressBar(
            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
            onValueChange = { fraction -> playbackManager.seekTo((fraction * duration).toLong()) },
            modifier = Modifier.weight(1f),
            activeColor = DesktopColors.Paper,
            inactiveColor = DesktopColors.Slate,
            hoverColor = DesktopColors.AcidLime,
            trackHeight = 3.dp,
            thumbSize = 8.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatTime(duration),
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = DesktopColors.Fog,
            modifier = Modifier.width(36.dp),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlayerTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(280)
            isVisible = true
        } else {
            isVisible = false
        }
    }

    val transition = updateTransition(targetState = isVisible, label = "PlayerTooltipTransition")
    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 160, easing = LinearOutSlowInEasing) },
        label = "PlayerTooltipAlpha"
    ) { if (it) 1f else 0f }
    val offsetY by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 180, easing = FastOutSlowInEasing) },
        label = "PlayerTooltipOffsetY"
    ) { if (it) 0f else 6f }
    val scale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 180, easing = FastOutSlowInEasing) },
        label = "PlayerTooltipScale"
    ) { if (it) 1f else 0.92f }

    val density = LocalDensity.current
    val positionProvider = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val gapPx = with(density) { 10.dp.roundToPx() }
                val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                val y = anchorBounds.top - popupContentSize.height - gapPx
                return IntOffset(
                    x = x.coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8)),
                    y = y.coerceAtLeast(8),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
        contentAlignment = Alignment.Center,
    ) {
        content()

        if (transition.currentState || transition.targetState) {
            Popup(
                popupPositionProvider = positionProvider,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = DesktopColors.CardElevated,
                    border = BorderStroke(1.dp, DesktopColors.Border),
                    shadowElevation = 6.dp,
                    modifier = Modifier.graphicsLayer {
                        this.alpha = alpha
                        this.translationY = offsetY
                        this.scaleX = scale
                        this.scaleY = scale
                    }
                ) {
                    Text(
                        text = text,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun BottomPlayerBar(
    player: VlcAudioPlayer,
    playbackManager: DesktopPlaybackManager,
    isNowPlayingOpen: Boolean = false,
    isLyricsOpen: Boolean = false,
    isConnectFlyoutOpen: Boolean = false,
    selectedDeviceId: String = "local_pc",
    onToggleNowPlaying: () -> Unit = {},
    onToggleLyrics: () -> Unit = {},
    onAddToPlaylist: ((DesktopSong) -> Unit)? = null,
    onToggleConnectFlyout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isPlaying by player.isPlaying.collectAsState()
    val currentTitle by player.currentTitle.collectAsState()
    val currentArtist by player.currentArtist.collectAsState()
    val currentThumbnailUrl by player.currentThumbnailUrl.collectAsState()
    val localVolume by player.volume.collectAsState()
    val isCastConnected by GoogleCastManager.isConnected.collectAsState()
    val castVolume by GoogleCastManager.castVolume.collectAsState()
    val isCastMuted by GoogleCastManager.isCastMuted.collectAsState()
    val connectedCastDevice by GoogleCastManager.connectedDevice.collectAsState()

    val currentDisplayVolume = if (isCastConnected) castVolume else localVolume
    val isDisplayMuted = if (isCastConnected) isCastMuted else (currentDisplayVolume == 0)

    val isShuffle by playbackManager.isShuffleEnabled.collectAsState()
    val repeatMode by playbackManager.repeatMode.collectAsState()
    val queue by playbackManager.queue.collectAsState()
    val currentIndex by playbackManager.currentIndex.collectAsState()
    val currentPlaylistTitle by playbackManager.currentPlaylistTitle.collectAsState()
    val currentSong by playbackManager.currentSong.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(DesktopColors.Surface),
    ) {
        // Garis batas atas (top border) pemisah player dengan area konten utama
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DesktopColors.Border)
                .align(Alignment.TopCenter)
        )

        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val barWidth = maxWidth
            val isNarrow = barWidth < 820.dp
            val isWide = barWidth > 1150.dp

            val trackInfoWidth = when {
                barWidth < 700.dp -> 180.dp
                barWidth < 1000.dp -> 220.dp
                barWidth < 1300.dp -> 260.dp
                else -> 300.dp
            }
            val volumeWidth = when {
                barWidth < 700.dp -> 80.dp
                barWidth < 1000.dp -> 95.dp
                else -> 110.dp
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // === 1. BAGIAN KIRI: Lagu, Cover Art & Tombol Simpan ===
                Row(
                    modifier = Modifier.width(trackInfoWidth),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Cover Art: 44.dp x 44.dp (Linear 6px radius)
                    PlayerTooltip(text = if (isNowPlayingOpen) "Tutup Layar Pemutar" else "Buka Layar Pemutar") {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(DesktopRadii.Button)
                                .clickable(enabled = currentSong != null) { onToggleNowPlaying() }
                        ) {
                            if (!currentThumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = currentThumbnailUrl,
                                    contentDescription = currentTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(DesktopRadii.Button),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(DesktopColors.Obsidian, DesktopRadii.Button),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = DesktopColors.Fog,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            if (isPlaying) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 2.dp)
                                        .clip(DesktopRadii.Badge)
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                ) {
                                    PlayingEqualizerBars(
                                        isPlaying = true,
                                        barWidth = 1.5.dp,
                                        maxHeight = 7.dp,
                                        barColor = DesktopColors.AcidLime,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Judul & Artis Lagu
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(DesktopRadii.Badge)
                            .clickable(enabled = currentSong != null) { onToggleNowPlaying() },
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = currentTitle.ifBlank { "Echo" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isNowPlayingOpen) DesktopColors.AcidLime else DesktopColors.Paper,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = currentArtist.ifBlank { "YouTube Music" },
                            fontSize = 11.sp,
                            color = DesktopColors.Fog,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Ikon Checkmark / Tambah ke Playlist
                    if (currentSong != null) {
                        PlayerTooltip(text = "Simpan ke Playlist") {
                            IconButton(
                                onClick = { onAddToPlaylist?.invoke(currentSong!!) },
                                modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Simpan ke Playlist",
                                    tint = DesktopColors.AcidLime,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(if (isNarrow) 8.dp else 16.dp))

                // === 2. BAGIAN TENGAH: Kontrol Pemutar & Timeline Seek Bar ===
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Tombol Kontrol
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        // Shuffle
                        PlayerTooltip(text = if (isShuffle) "Matikan Acak" else "Acak Lagu") {
                            Box(contentAlignment = Alignment.BottomCenter) {
                                IconButton(
                                    onClick = { playbackManager.toggleShuffle() },
                                    modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                                ) {
                                    Icon(
                                        Icons.Default.Shuffle,
                                        contentDescription = "Acak Playlist",
                                        tint = if (isShuffle) DesktopColors.AcidLime else DesktopColors.Fog,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                if (isShuffle) {
                                    Box(
                                        modifier = Modifier
                                            .padding(bottom = 1.dp)
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(DesktopColors.AcidLime)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Lagu Sebelumnya (Previous)
                        PlayerTooltip(text = "Lagu Sebelumnya") {
                            IconButton(
                                onClick = { playbackManager.playPrevious() },
                                enabled = playbackManager.hasPrevious,
                                modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(
                                    Icons.Default.SkipPrevious,
                                    contentDescription = "Lagu Sebelumnya",
                                    tint = if (playbackManager.hasPrevious) DesktopColors.Paper else DesktopColors.Border,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Play / Pause: High-emphasis Acid Lime action
                        PlayerTooltip(text = if (isPlaying) "Jeda" else "Putar") {
                            IconButton(
                                onClick = { playbackManager.togglePlayPause() },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(DesktopColors.AcidLime)
                                    .pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Jeda" else "Putar",
                                    tint = DesktopColors.Void,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Lagu Berikutnya (Next)
                        PlayerTooltip(text = "Lagu Berikutnya") {
                            IconButton(
                                onClick = { playbackManager.playNext() },
                                enabled = playbackManager.hasNext,
                                modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = "Lagu Berikutnya",
                                    tint = if (playbackManager.hasNext) DesktopColors.Paper else DesktopColors.Border,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Repeat
                        val repeatTooltipText = when (repeatMode) {
                            RepeatMode.ONE -> "Ulangi: 1 Lagu"
                            RepeatMode.ALL -> "Ulangi: Semua"
                            RepeatMode.NONE -> "Ulangi: Mati"
                        }
                        PlayerTooltip(text = repeatTooltipText) {
                            Box(contentAlignment = Alignment.BottomCenter) {
                                IconButton(
                                    onClick = { playbackManager.toggleRepeat() },
                                    modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                                ) {
                                    Icon(
                                        if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                        contentDescription = "Ulangi Putaran",
                                        tint = if (repeatMode != RepeatMode.NONE) DesktopColors.AcidLime else DesktopColors.Fog,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                if (repeatMode != RepeatMode.NONE) {
                                    Box(
                                        modifier = Modifier
                                            .padding(bottom = 1.dp)
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(DesktopColors.AcidLime)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Seek Progress Bar (Garis Ramping 4dp dengan Waktu Kiri & Kanan terisolasi rekomposisinya)
                    PlayerSeekBarRow(
                        player = player,
                        playbackManager = playbackManager,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                    )
                }

                Spacer(modifier = Modifier.width(if (isNarrow) 8.dp else 16.dp))

                // === 3. BAGIAN KANAN: Lirik, Antrean, Volume & Layar Penuh ===
                Row(
                    modifier = Modifier.widthIn(min = trackInfoWidth),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    // Tombol Lirik / Mic
                    PlayerTooltip(text = if (isLyricsOpen) "Tutup Lirik" else "Lirik Lagu") {
                        IconButton(
                            onClick = onToggleLyrics,
                            enabled = currentSong != null,
                            modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Lirik Lagu",
                                tint = if (isLyricsOpen) DesktopColors.AcidLime else DesktopColors.Fog,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }

                    // Tombol Antrean (Queue)
                    PlayerTooltip(text = if (isNowPlayingOpen) "Tutup Antrean" else "Antrean Lagu") {
                        IconButton(
                            onClick = onToggleNowPlaying,
                            enabled = currentSong != null,
                            modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Antrean Lagu",
                                tint = if (isNowPlayingOpen) DesktopColors.AcidLime else DesktopColors.Fog,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Connect to a device (Hubungkan ke Perangkat / Cast - Spotify Style)
                    val isRemoteSelected = isCastConnected || selectedDeviceId != "local_pc"
                    val isConnectActive = isRemoteSelected || isConnectFlyoutOpen
                    val castTooltipText = if (isCastConnected && connectedCastDevice != null) {
                        "Terhubung ke ${connectedCastDevice!!.name}"
                    } else if (isRemoteSelected) {
                        "Terhubung ke perangkat eksternal"
                    } else {
                        "Connect to a device"
                    }
                    PlayerTooltip(text = castTooltipText) {
                        Box(contentAlignment = Alignment.BottomCenter) {
                            IconButton(
                                onClick = onToggleConnectFlyout,
                                modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                            ) {
                                Icon(
                                    Icons.Default.Devices,
                                    contentDescription = "Hubungkan ke perangkat",
                                    tint = if (isConnectActive) DesktopColors.AcidLime else DesktopColors.Fog,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            if (isConnectActive) {
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 1.dp)
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(DesktopColors.AcidLime),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Tombol Mute / Volume
                    var previousVolume by remember { mutableStateOf(50) }
                    PlayerTooltip(text = if (isDisplayMuted) "Nyalakan Suara" else "Bisukan Suara") {
                        IconButton(
                            onClick = {
                                if (isCastConnected) {
                                    if (isDisplayMuted || currentDisplayVolume == 0) {
                                        // Unmute TV secara aman tanpa melonjak ke volume keras
                                        val targetVol = if (currentDisplayVolume in 5..60) currentDisplayVolume else if (previousVolume in 10..50) previousVolume else 25
                                        GoogleCastManager.setVolume(targetVol)
                                    } else {
                                        previousVolume = currentDisplayVolume
                                        GoogleCastManager.setMuted(true)
                                    }
                                } else {
                                    if (localVolume > 0) {
                                        previousVolume = localVolume
                                        playbackManager.setVolume(0)
                                    } else {
                                        playbackManager.setVolume(if (previousVolume > 0) previousVolume else 50)
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                if (isDisplayMuted) Icons.AutoMirrored.Filled.VolumeOff
                                else if (currentDisplayVolume < 50) Icons.AutoMirrored.Filled.VolumeDown
                                else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isDisplayMuted) "Nyalakan Suara" else "Bisukan Suara",
                                tint = if (isDisplayMuted) DesktopColors.CoralRed else DesktopColors.Fog,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }

                    // Volume Bar (Ramping 3dp) - Hijau Acid Lime saat terhubung ke TV layaknya Spotify Connect
                    SpotifyStyleProgressBar(
                        value = if (isDisplayMuted) 0f else (currentDisplayVolume.toFloat() / 100f),
                        onValueChange = { fraction ->
                            val target = (fraction * 100).toInt()
                            if (isCastConnected) {
                                GoogleCastManager.setVolume(target)
                            } else {
                                playbackManager.setVolume(target)
                            }
                        },
                        modifier = Modifier.width(volumeWidth),
                        activeColor = if (isCastConnected) DesktopColors.AcidLime else DesktopColors.Paper,
                        inactiveColor = DesktopColors.Slate,
                        hoverColor = DesktopColors.AcidLime,
                        trackHeight = 3.dp,
                        thumbSize = 8.dp,
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Layar Penuh (Fullscreen / Now Playing)
                    PlayerTooltip(text = if (isNowPlayingOpen) "Keluar dari Layar Penuh" else "Layar Penuh") {
                        IconButton(
                            onClick = onToggleNowPlaying,
                            enabled = currentSong != null,
                            modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon.Hand),
                        ) {
                            Icon(
                                if (isNowPlayingOpen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isNowPlayingOpen) "Keluar dari Layar Penuh" else "Layar Penuh",
                                tint = if (isNowPlayingOpen) DesktopColors.AcidLime else DesktopColors.Fog,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.UnifiedPlayerLayer(
    player: VlcAudioPlayer,
    repository: DesktopMusicRepository,
    playbackManager: DesktopPlaybackManager,
    progress: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    springSpec: AnimationSpec<Float>,
    onClose: () -> Unit,
    onOpenLyrics: () -> Unit,
    onAddToPlaylist: (DesktopSong) -> Unit,
    isConnectFlyoutOpen: Boolean = false,
    selectedDeviceId: String = "local_pc",
    onToggleConnectFlyout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Drag velocity tracker untuk spring release
    var dragVelocityY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val p = progress.value.coerceIn(0f, 1f)
                val fullW = constraints.maxWidth
                val fullH = constraints.maxHeight

                val miniH = with(density) { 76.dp.roundToPx() }
                val currentH = (miniH + (fullH - miniH) * p).toInt()

                val placeable = measurable.measure(
                    androidx.compose.ui.unit.Constraints.fixed(fullW, currentH)
                )

                layout(fullW, currentH) {
                    placeable.place(0, 0)
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragVelocityY = 0f },
                    onDragEnd = {
                        scope.launch {
                            val current = progress.value
                            val target = when {
                                dragVelocityY < -200f -> 1f
                                dragVelocityY > 200f -> 0f
                                current > 0.45f -> 1f
                                else -> 0f
                            }
                            val initVel = (-dragVelocityY / 300f).coerceIn(-10f, 10f)
                            progress.animateTo(
                                targetValue = target,
                                animationSpec = springSpec,
                                initialVelocity = initVel,
                            )
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            val target = if (progress.value > 0.5f) 1f else 0f
                            progress.animateTo(target, springSpec)
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragVelocityY = dragAmount
                        // dragAmount < 0: gesture mouse ke atas (expand ke fullscreen)
                        // dragAmount > 0: gesture mouse ke bawah (collapse ke mini)
                        val delta = -dragAmount / 350f
                        scope.launch {
                            progress.snapTo((progress.value + delta).coerceIn(0f, 1f))
                        }
                    }
                )
            }
            .graphicsLayer {
                clip = true
            }
            .background(DesktopColors.Surface)
    ) {
        // Konten Mini Player: p memudar cepat di paruh awal (0.0f .. 0.35f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    alpha = ((0.35f - p) / 0.35f).coerceIn(0f, 1f)
                }
        ) {
            if (progress.value < 0.6f) {
                BottomPlayerBar(
                    player = player,
                    playbackManager = playbackManager,
                    isNowPlayingOpen = false,
                    isLyricsOpen = false,
                    isConnectFlyoutOpen = isConnectFlyoutOpen,
                    selectedDeviceId = selectedDeviceId,
                    onToggleNowPlaying = {
                        scope.launch { progress.animateTo(1f, springSpec) }
                    },
                    onToggleLyrics = onOpenLyrics,
                    onAddToPlaylist = onAddToPlaylist,
                    onToggleConnectFlyout = onToggleConnectFlyout,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            if (progress.value < 0.1f) {
                                scope.launch { progress.animateTo(1f, springSpec) }
                            }
                        }
                )
            }
        }

        // Konten Fullscreen Player: p muncul di paruh kedua (0.40f .. 1.0f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    alpha = ((p - 0.40f) / 0.60f).coerceIn(0f, 1f)
                }
        ) {
            if (progress.value > 0.3f) {
                NowPlayingView(
                    repository = repository,
                    playbackManager = playbackManager,
                    onClose = {
                        scope.launch { progress.animateTo(0f, springSpec) }
                    },
                    onAddToPlaylist = onAddToPlaylist,
                    onOpenLyrics = onOpenLyrics,
                )
            }
        }
    }
}


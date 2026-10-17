# Working with Metrolist Desktop as an AI agent

**IMPORTANT NOTICE:** This workspace is specifically for **Metrolist Desktop (Windows)**, NOT the Android application. This is maintained in a personal, independent repository separate from the official MetrolistGroup repository.

---

## 1. Project Scope & Architecture

- **Primary Target:** Windows Desktop (`:desktop` module).
- **Audio Engine:** LibVLC 64-bit (`vlcj`) through [`VlcAudioPlayer.kt`](file:///D:/meteor_2/Metrolist/desktop/src/desktopMain/kotlin/com/metrolist/desktop/player/VlcAudioPlayer.kt).
- **Queue & Playback Management:** [`DesktopPlaybackManager.kt`](file:///D:/meteor_2/Metrolist/desktop/src/desktopMain/kotlin/com/metrolist/desktop/player/DesktopPlaybackManager.kt).
- **UI Framework:** Compose Multiplatform Desktop with modern dark theme.
- **Repository Separation:** Do NOT assume connection or dependencies on `MetrolistGroup/Metrolist` releases or issues. This is an independent personal repository.

---

## 2. Playback & Shuffle Rules (Anti-Repeat)

- **History Preservation:** When shuffle is toggled on or active, all tracks already played before the current track MUST be preserved in `playedHistory` in the past.
- **No Reshuffle Overwrite:** Already-played tracks must never be re-injected into the upcoming unplayed queue in the same cycle.
- **Navigation:** Back navigation (`playPrevious()`) must allow going back through played history.

---

## 3. Building and Testing

1. **Run Desktop Unit Tests:**
   ```bash
   ./gradlew desktopTest
   ```
2. **Compile Desktop Code:**
   ```bash
   ./gradlew :desktop:compileKotlinDesktop
   ```
3. **Run Desktop Application:**
   ```bash
   ./gradlew :desktop:run
   ```
4. **Package Windows Installer:**
   ```bash
   ./gradlew :desktop:packageMsi
   ./gradlew :desktop:packageExe
   ```

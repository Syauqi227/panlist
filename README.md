<div align="center">

<img src="desktop/src/desktopMain/resources/icons/icon.png" alt="Echo app icon" width="160" />

# Echo

### Lightweight YouTube Music Client for Windows Desktop

<br/>

[![Latest Release](https://img.shields.io/github/v/release/Syauqi227/panlist?style=for-the-badge&labelColor=0d1117)](https://github.com/Syauqi227/panlist/releases)
[![License](https://img.shields.io/github/license/Syauqi227/panlist?style=for-the-badge&labelColor=0d1117)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010%20%7C%2011-0078D6?style=for-the-badge&logo=windows&logoColor=white&labelColor=0d1117)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose%20Desktop-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white&labelColor=0d1117)](#tech-stack)

<br/>

[**Features**](#features) · [**Requirements**](#requirements) · [**Download**](#download) · [**Build from Source**](#build-from-source)

</div>

---

## Overview

**Echo** is an independent, fast, and lightweight native Windows desktop music player powered by the YouTube Music catalog. Built with **Compose Multiplatform Desktop** and **LibVLC**, Echo delivers ad-free audio streaming, offline playback, instant Spotify playlist importing, and responsive Linear-inspired dark UI without heavy browser memory overhead.

---

## <a id="features"></a>Features

<table>
  <tr>
    <td width="50%" valign="top">

#### Playback & Audio
- Stream any song from the YouTube Music catalog
- Powered by **LibVLC (`vlcj`)** for stable, low-memory native playback
- **Offline Download & Playback** — download tracks locally and listen 100% offline
- **Smart Shuffle (Anti-Repeat)** — preserves played history (`playedHistory`) and prevents repeating tracks in the same cycle
- **Windows Media Keys & SMTC** — control playback seamlessly with hardware media keys
- **Connect to TV** — pair and cast playback directly to YouTube on smart TVs (Lounge API)

</td>
    <td width="50%" valign="top">

#### Library, Lyrics & Interface
- **Spotify Playlist Importer** — import public Spotify playlists directly into Echo without needing a Spotify account
- **Local Playlists** — create, manage, and persist playlists locally on your PC
- **Live Synced Lyrics** — time-synced lyrics integration for currently playing tracks
- **Linear Modern Dark UI** — clean, distraction-free dark interface with custom window title bar integration
- **In-App Updater** — check and install updates directly from GitHub Releases

</td>
  </tr>
</table>

---

## <a id="requirements"></a>Requirements

- **Operating System:** Windows 10 or Windows 11 (64-bit)
- **VLC Media Player (64-bit):** Required on the system for the LibVLC (`vlcj`) audio engine
- **For Development / Building from Source:** JDK 21 (JetBrains Runtime 21 recommended for custom window decorations)

---

## <a id="download"></a>Download

Get the latest Windows installer (`.msi` / `.exe`) from the official repository releases page:

- [**GitHub Releases (`Syauqi227/panlist`)**](https://github.com/Syauqi227/panlist/releases/latest)

---

## <a id="build-from-source"></a>Build from Source

Clone the repository and run the Gradle tasks from the project directory:

```bash
git clone https://github.com/Syauqi227/panlist.git
cd panlist
```

### 1. Run Desktop App (Development)
```bash
./gradlew :desktop:run
```
*(Or run `run-desktop.bat` on Windows)*

### 2. Run Desktop Unit Tests
```bash
./gradlew desktopTest
```

### 3. Compile Desktop Module
```bash
./gradlew :desktop:compileKotlinDesktop
```

### 4. Package Windows Installer (`.msi` / `.exe`)
```bash
./gradlew :desktop:packageMsi
./gradlew :desktop:packageExe
```

---

## <a id="tech-stack"></a>Tech Stack

- **UI Framework:** Compose Multiplatform Desktop (Material 3 + Linear Modern Design System)
- **Audio Engine:** LibVLC / VLCJ (`uk.co.caprica:vlcj`)
- **Networking & Extractor:** Kotlin Coroutines & Ktor (`:innertube` client)
- **Image Loading:** Coil 3 Multiplatform
- **Dependency Injection:** Koin

---

## Project Background & Origins

> [!NOTE]
> - **100% AI-Assisted:** This project was built and ported entirely using AI coding assistants.
> - **Derivative of Metrolist:** Echo is a Windows desktop adaptation derived from the open-source [Metrolist](https://github.com/metrolistgroup/metrolist) project, re-architected to run natively on Windows using Compose Multiplatform and LibVLC.
> - **Personal Project Made Public:** It was initially intended strictly for personal daily use in a private repository. It was made public primarily to enable seamless in-app updater functionality and installer distribution via GitHub Releases.

---

## Disclaimer

This project is an independent open-source application and is **not affiliated with, funded, authorized, endorsed by, or in any way associated** with YouTube, Google LLC, Spotify AB, or any of their affiliates and subsidiaries. All trademarks and intellectual property rights belong to their respective owners.

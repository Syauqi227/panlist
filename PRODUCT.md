# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Stack

Compose Multiplatform (Desktop Windows) & Kotlin JVM, LibVLC / VLCJ audio engine, InnerTube YouTube Music API client (Ktor), Local JSON playlist persistence.

## Users

Pengguna desktop Windows yang ingin mendengarkan katalog musik YouTube Music secara ringan, cepat, tanpa iklan, dan bebas dari konsumsi memori browser yang berat, serta ingin membawa dan memutar playlist dari Spotify dengan mudah.

## Product Purpose

Menyediakan pemutar musik desktop native yang cepat, bersih, dan independen untuk katalog YouTube Music, dengan kemampuan membuat playlist lokal dan mengonversi tautan playlist Spotify secara instan tanpa memerlukan akun atau login Spotify.

## Positioning

Klien YouTube Music desktop native Windows yang fokus pada performa ringan, kontrol shortcut media keyboard sistem (SMTC), dan kemampuan instan konversi playlist Spotify langsung ke stream audio YouTube Music tanpa iklan dan tanpa dependensi browser.

## Operating Context

Aplikasi berjalan di lingkungan desktop Windows (Windows 10 & 11) sebagai pemutar musik di latar belakang (*background audio player*), dikontrol melalui antarmuka visual desktop dan tombol media keyboard fisik (Play/Pause, Next, Seek, Volume).

## Capabilities and Constraints

- **Streaming YouTube Music:** Mengambil stream audio langsung dari server YouTube (memerlukan koneksi internet aktif).
- **Import Spotify:** Mengurai tautan publik playlist Spotify dan mencocokkannya ke katalog YouTube Music secara otomatis dan paralel.
- **Penyimpanan Playlist Lokal:** Metadata playlist dan lagu disimpan secara persisten di komputer pengguna (`%APPDATA%/Metrolist/playlists.json`).
- **Audio Engine:** Menggunakan binding LibVLC (VLC 64-bit) untuk pemutaran audio stabil di Windows.
- **Konektivitas Internet & Offline Playback:** Streaming audio dan pencarian lagu membutuhkan koneksi internet. Lagu yang telah diunduh via tombol "Download Offline" tersimpan lokal di `%APPDATA%/Metrolist/downloads/` dan dapat diputar 100% offline tanpa internet sama sekali.

## Brand Commitments

- Nama Produk: Metrolist Desktop
- Gaya Visual: Tema gelap modern (*dark mode*), aksen utama merah muda-magenta (`#FF3366`) dan aksen hijau Spotify (`#1DB954`) untuk fitur import.
- Filosofi UX: Bersih, bebas iklan, responsif, dan siap pakai tanpa kewajiban login.

## Evidence on Hand

- Modul desktop Compose Multiplatform di `desktop/` (Home, Search, Playlist, Settings).
- Ekstraktor stream audio berbasis `InnerTubeExtractor` yang telah teruji pada desktop JVM.
- Integrasi import Spotify embed parser yang terverifikasi mengekstrak metadata dan mencocokkan lagu ke YouTube Music.
- Mesin download dan pemutaran audio offline mandiri berbasis `OfflineDownloadManager` yang terverifikasi memutar file audio lokal tanpa internet.
## Product Principles

1. **Lightweight & Fast:** Pemutar musik harus ringan, terbuka cepat, dan hemat sumber daya CPU/RAM saat berjalan di latar belakang.
2. **Zero Friction:** Pengguna dapat langsung mendengarkan musik di Home dan mengimpor playlist Spotify tanpa hambatan login akun.
3. **Local Ownership:** Playlist yang dibuat atau diimpor tersimpan secara persisten di komputer pengguna secara mandiri.
4. **Reliable Playback:** Menjamin audio stream berhasil diputar menggunakan mesin LibVLC dengan integrasi kontrol keyboard media Windows.

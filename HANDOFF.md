# Metrolist Desktop — Session Handoff & Resume Guide

Dokumen ini disiapkan agar agent atau sesi di **Antigravity Desktop** dapat langsung melanjutkan pengembangan project ini tanpa kehilangan konteks.

---

## 1. Lokasi Workspace & Struktur Project

- **Direktori Root**: `D:/meteor/Metrolist`
- **Sub-Modul yang Baru Dibuat**: `desktop/` (Compose Multiplatform Desktop untuk Windows)
- **Modul Terkait**:
  - `innertube/` — API extractor YouTube Music (sudah dikonfigurasi ke Kotlin JVM untuk desktop).
  - `app/` — Aplikasi Android asli Metrolist.

---

## 2. Fitur yang Sudah Selesai Dibuat & Terverifikasi

### A. Fitur Playlist Lengkap
1. **Model Data (`desktop/src/desktopMain/kotlin/com/metrolist/desktop/model/PlaylistModels.kt`)**:
   - `DesktopSong`, `DesktopPlaylistSummary`, dan `DesktopPlaylist` dengan `@Serializable`.
2. **Penyimpanan Lokal Persisten (`desktop/src/desktopMain/kotlin/com/metrolist/desktop/repository/LocalPlaylistStore.kt`)**:
   - Menggunakan format JSON di `~/.metrolist/playlists.json` dengan thread-safety Mutex.
   - Operasi CRUD: Buat playlist, hapus playlist, tambah lagu (anti-duplikat), hapus lagu.
   - Otomatis membuat playlist default *"Favorit Saya"*.
3. **Integrasi Online YouTube Music (`desktop/src/desktopMain/kotlin/com/metrolist/desktop/repository/DesktopMusicRepository.kt`)**:
   - `getHomePlaylists()`: Playlist kurasi YouTube Music.
   - `searchPlaylists(query)`: Pencarian playlist Featured & Community.
   - `getPlaylistDetails(id)`: Rincian lagu dalam playlist (lokal maupun YouTube).
   - `extractPlaylistId(url)`: Ekstraksi ID dari URL YouTube Music maupun link standar.
4. **Antarmuka Compose Desktop (`desktop/src/desktopMain/kotlin/com/metrolist/desktop/ui/`)**:
   - `PlaylistView.kt`:
     - *Overview*: Kategori filter, bar URL input, grid kartu playlist.
     - *Detail*: Hero section adaptif, tombol Putar Semua, Acak, Simpan ke Lokal, Hapus.
     - *Tombol `+ Tambah Lagu`*: Tombol di action bar dan empty state playlist lokal.
   - `AddSongDirectlyDialog.kt`: Dialog pencarian YouTube Music dan penambahan lagu instan ke playlist yang sedang dibuka.
   - `AddToPlaylistDialog.kt`: Dialog untuk menambahkan lagu dari Beranda/Cari ke salah satu playlist lokal.
   - `CreatePlaylistDialog.kt`: Modal pembuatan playlist baru.
   - `ImportSpotifyDialog.kt`: Importer playlist publik Spotify ke playlist lokal.

### B. Pemutar & Antrean Lagu
- `DesktopPlaybackManager.kt` (`desktop/.../player/`):
  - Mengelola antrean lagu (`queue`), indeks saat ini, mode shuffle, dan repeat (`NONE`, `ALL`, `ONE`).
  - Auto-advance ke lagu berikutnya saat lagu selesai diputar (`onPlaybackFinished`).
- `VlcAudioPlayer.kt`: Integrasi audio engine LibVLC / VLCJ.
- `WindowsMediaControls.kt`: Pintasan keyboard multimedia Windows (`MediaPlayPause`, `MediaNext`, `MediaPrevious`, `Ctrl+Alt+Panah`).

### C. Download Offline Berkecepatan Tinggi
- `OfflineDownloadManager.kt` (`desktop/.../download/`):
  - **Chunked HTTP Range Requests**: File dipecah menjadi *chunk* 1 MB dengan header `Range: bytes=start-end`. Mencegah server YouTube CDN mencekik bandwidth (*throttling* ke 16 KB/s), sehingga lagu terunduh dalam 1–2 detik pada kecepatan penuh jaringan.
  - **Konkuren Paralel**: `downloadPlaylist` mengunduh 3 lagu bersamaan menggunakan `Semaphore(3)`.

### D. Responsivitas & Copywriting
- `BottomPlayerBar` menggunakan `BoxWithConstraints` (adaptif ke layar sempit, sedang, dan lebar).
- Tombol aksi hero playlist menggunakan `FlowRow` agar terlipat rapi tanpa terpotong di layar laptop.
- Copywriting bahasa Indonesia yang konsisten: *Beranda*, *Cari*, *Playlist*, *Pengaturan*.

---

## 3. Status Binary & Executable

File executable Windows telah dikompilasi dan siap dijalankan:
```text
D:\meteor\Metrolist\desktop\build\compose\binaries\main\app\Metrolist\Metrolist.exe
```

---

## 4. Perintah Verifikasi & Build (CMD / Terminal)

Jalankan perintah ini dari direktori `D:/meteor/Metrolist`:

```bash
# Kompilasi kode desktop:
cmd /c "gradlew.bat :desktop:compileKotlinDesktop"

# Menjalankan unit test desktop:
cmd /c "gradlew.bat :desktop:desktopTest"

# Membangun ulang Metrolist.exe distributable:
cmd /c "gradlew.bat :desktop:createDistributable"
```

---

## 5. Cara Melanjutkan di Antigravity Desktop

1. **Buka Folder Project**:
   - Di Antigravity Desktop: `File` $\rightarrow$ `Open Folder` $\rightarrow$ Pilih `D:\meteor\Metrolist`.
2. **Kirim Prompt Lanjutan ke AI Agent Antigravity**:
   Salin dan tempel pesan berikut ke chat agent Antigravity:
   > *"Halo, lanjutkan pengembangan Metrolist Desktop dari sesi sebelumnya. Silakan baca file `HANDOFF.md` untuk memahami arsitektur, fitur yang sudah selesai, dan perintah build yang digunakan. Status saat ini: fitur playlist, fast download, dan responsivitas UI sudah selesai dan lulus semua unit test. Tugas berikutnya adalah: [sebutkan tugas/fitur baru yang ingin Anda kerjakan]."*

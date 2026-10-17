# Panduan Agent AI: Echo (Desktop Windows)

Dokumen ini adalah instruksi utama bagi seluruh Agent AI yang bekerja di dalam repositori ini.

---

## 1. Fokus Proyek: Echo Desktop (BUKAN Android)

- **Fokus Utama:** Proyek workspace ini didedikasikan secara khusus untuk mengembangkan dan mengelola **Echo Desktop (Windows)** (sebelumnya bernama Metrolist Desktop), bukan aplikasi Android.
- **Nama Aplikasi:** **Echo** (Package Name: `Echo`).
- **Modul Utama:** Modul aktif utama adalah **`:desktop`** (Compose Multiplatform / Desktop) dan modul pendukung **`:innertube`** (Ktor HTTP client / data YouTube Music).
- **Arsitektur Pemutar Desktop:**
  - Menggunakan **LibVLC (`vlcj`)** via [`VlcAudioPlayer.kt`](file:///D:/meteor_2/Metrolist/desktop/src/desktopMain/kotlin/com/metrolist/desktop/player/VlcAudioPlayer.kt), bukan Android ExoPlayer / Media3.
  - Pengelolaan antrean (*queue*), shuffle, dan status pemutaran dikelola oleh [`DesktopPlaybackManager.kt`](file:///D:/meteor_2/Metrolist/desktop/src/desktopMain/kotlin/com/metrolist/desktop/player/DesktopPlaybackManager.kt).
  - UI dibangun menggunakan **Compose Multiplatform (Desktop)** dengan sistem desain Linear Modern (`DesktopTheme.kt`, `PlaylistView.kt`, `NowPlayingView.kt`).
- **PENTING:** Jangan mengarahkan solusi atau modifikasi ke modul `:app` (Android) kecuali pengguna secara gamblang meminta perubahan khusus untuk platform Android.

---

## 2. Repositori Mandiri (Terpisah dari MetrolistGroup)

- Proyek ini dikelola secara independen pada **repositori pribadi milik pengguna**:
  - **URL Repositori:** [`https://github.com/Syauqi227/panlist.git`](https://github.com/Syauqi227/panlist.git)
  - **Owner / Repo:** `Syauqi227/panlist`
- Terpisah sepenuhnya dari repositori upstream resmi `MetrolistGroup/Metrolist`.
- Jangan mencoba melakukan sinkronisasi, push, pull request, atau mengasumsikan ketergantungan rilis ke repositori MetrolistGroup resmi.
- Segala bentuk fitur pembaruan (*in-app updater*), rilis, atau integrasi harus merujuk ke repositori GitHub pribadi ini (`Syauqi227/panlist`).

---

## 3. Aturan Pemutaran & Shuffle (Anti-Repeat)

- **Preservasi Riwayat Putar (*History Preservation*):**
  - Ketika mode acak (*shuffle*) diaktifkan di tengah pemutaran, lagu-lagu yang sudah selesai diputar sebelum lagu aktif harus **tetap dipertahankan di bagian riwayat lampau (`playedHistory`)**.
  - Lagu yang sudah diputar **dilarang dimasukkan kembali ke antrean acak mendatang** dalam siklus yang sama.
  - Tombol *Previous* (`playPrevious()`) harus tetap dapat menavigasi mundur ke lagu-lagu yang sudah diputar tersebut.
  - Mode Repeat All (`RepeatMode.ALL`) hanya boleh memutar ulang lagu dari awal setelah seluruh lagu dalam antrean selesai diputar tepat satu kali.

---

## 4. Perintah Build & Testing Desktop

Gunakan perintah Gradle berikut dari root direktori proyek (`Metrolist/`):

1. **Menjalankan Unit Test Desktop:**
   ```bash
   ./gradlew desktopTest
   ```
2. **Kompilasi Modul Desktop:**
   ```bash
   ./gradlew :desktop:compileKotlinDesktop
   ```
3. **Menjalankan Aplikasi Desktop:**
   ```bash
   ./gradlew :desktop:run
   ```
4. **Membuat Paket Installer Windows (.msi / .exe):**
   ```bash
   ./gradlew :desktop:packageMsi
   ./gradlew :desktop:packageExe
   ```

---

## 5. Pedoman Pengembangan untuk Agent

1. Selalu jalankan `./gradlew desktopTest` setelah memodifikasi logika pemutar atau antrean desktop untuk memastikan tidak ada regresi.
2. Jaga kebersihan dan performa: hindari operasi I/O berat di main UI thread Compose; gunakan coroutines (`Dispatchers.IO` atau `Dispatchers.Default`).
3. Pertahankan dokumentasi dan struktur kode yang rapi.

# E-Journal • SMKN 2 Tanjung 📓🏢

**E-Journal** adalah aplikasi manajemen rekapitulasi pekerjaan harian dan pelaporan bulanan kedinasan yang dirancang khusus untuk memfasilitasi administrasi kerja staf Tata Usaha di lingkungan sekolah. Aplikasi ini beroperasi secara *offline-first* dengan jaminan privasi data tersimpan aman di perangkat masing-masing, serta menghasilkan berkas cetak **Laporan Bulanan PDF A4** berstandar tata naskah dinas resmi.

Tersedia dalam dua varian terpadu: **Aplikasi Android Native** dan **Web Companion PWA (Progressive Web App)** untuk pengguna iPhone (iOS) maupun komputer.

---

## ✨ Fitur Utama

### 1. 📝 Pencatatan Jurnal Harian (*Daily Work Log*)
* **Multi-Tugas per Tanggal**: Memungkinkan pencatatan beberapa uraian kegiatan kerja sekaligus dalam satu tanggal yang sama.
* **Multi-Foto per Kegiatan**: Mendukung lampiran lebih dari 1 foto bukti fisik per kegiatan (hingga 10 foto) dengan pratinjau thumbnail dan opsi hapus per foto.
* **Kalender & Tanggal Fleksibel**: Pemilihan hari dan tanggal kerja dinamis dengan antarmuka kalender interaktif.
* **Bukti Dukung Lengkap**:
  * **Bukti Teks**: Nomor surat dinas, tautan berkas cloud, atau ringkasan hasil pekerjaan.
  * **Bukti Foto Fisik**: Ambil foto langsung melalui kamera atau pilih dari galeri perangkat.
* **Pencarian Cepat (*Instant Search*)**: Filter kegiatan harian secara *real-time* berdasarkan kata kunci tugas, tanggal, atau bukti dukung.

### 2. 📄 Generator Laporan Bulanan PDF A4 Standar Dinas
* **Tabel Rekapitulasi Terstruktur**: Pengelompokan rapi 1 baris per tanggal kerja dengan penomoran urut otomatis, tipografi **Roboto 11 pt**, tata letak rata tengah, line spacing 1.0, serta thumbnail selebar kolom (1 foto per kegiatan).
* **Lembar Pengesahan Tanda Tangan 3 Pihak**: Ruang tanda tangan leluasa (+2 enter) untuk penandatanganan fisik/stempel resmi:
  1. Pegawai Yang Bersangkutan (Penyusun Laporan)
  2. Memeriksa / Mengetahui: Kepala Sub Bagian Tata Usaha (Atasan Langsung)
  3. Mengesahkan: Kepala Sekolah (Pimpinan Instansi)
* **Lampiran Khusus Foto Opsional & Kolase Terpadu**:
  * Tersedia toggle/sakelar untuk menyertakan atau mengecualikan lembar lampiran foto.
  * Susunan **Kolase Adaptif Terpadu per Kegiatan** (1 foto: ukuran besar tengah; 2 foto: 2 kolom; 3 foto: 3 kolom sejajar; 4 foto: grid 2×2; 5+ foto: grid 3 kolom).
  * Header lampiran bulanan hanya dicetak satu kali di lembar pertama lampiran.
* **Penomoran Halaman Seragam**: Tercetak nomor halaman resmi (`Hal. X dari Y`) di seluruh lembar PDF.
* **Pratinjau & Berbagi Berkas**: Langsung tersimpan ke direktori Download perangkat dan siap dicetak atau dibagikan via WhatsApp, Email, dan Google Drive.

### 3. 👤 Profil Pegawai & Pengesahan
* Pengaturan data identitas staf penyusun laporan (Nama, NIP/NUPTK, Jabatan, Unit Kerja, Kota Penetapan).
* Konfigurasi data pejabat atasan langsung (Kasubbag TU) dan pimpinan sekolah (Kepala Sekolah).
* Pengaturan jadwal notifikasi pengingat harian.

### 4. 🔄 Pembaruan Otomatis di Dalam Aplikasi (*In-App Auto-Updater*)
* Terhubung langsung ke repositori resmi GitHub Releases.
* **Tanpa Perlu Bongkar-Pasang**: Pembaruan dapat diunduh dan dipasang langsung di dalam aplikasi tanpa perlu melakukan *uninstall*.
* **Data Aman**: Seluruh data catatan kegiatan di database lokal SQLite tetap tersimpan utuh saat pembaruan berlangsung.

### 5. 🛡️ Privasi & Keamanan Data (100% Client-Side)
* **Tanpa Database Server Eksternal**: Seluruh data jurnal dan foto tersimpan lokal di memori HP/browser masing-masing.
* **Sanitasi Gambar Otomatis**: Memverifikasi berkas media, membatasi resolusi untuk mencegah *decompression bomb*, serta menghapus metadata sensitif EXIF dan koordinat GPS.
* **Bebas API Key & Kredensial**: Sistem tidak menggunakan layanan pihak ketiga berbayar yang rentan kebocoran kunci rahasia.

---

## 📱 Akses & Pemasangan

### A. Pengguna Android (Smartphone Android)
Unduh file APK rilis resmi terbaru yang telah ditandatangani (*signed*):
* 🔗 **[Unduh APK Resmi di GitHub Releases](https://github.com/rafli1326/E-Journal-Skaduta/releases)**
* Pasang berkas `.apk` pada perangkat Android Anda (mendukung Android 8.0 Oreo hingga Android 15).

### B. Pengguna iPhone (iOS Safari) & Desktop
Gunakan versi Progressive Web App (PWA) tanpa perlu biaya langganan:
* 🔗 **[Buka Web PWA E-Journal Skaduta](https://rafli1326.github.io/E-Journal-Skaduta/)**
* **Cara pasang di iPhone**: Buka tautan di browser **Safari** $\rightarrow$ ketuk tombol **Share (Bagikan)** $\rightarrow$ pilih **"Tambah ke Layar Utama" (*Add to Home Screen*)**. Aplikasi akan langsung muncul di layar utama iPhone dan siap digunakan secara offline.

---

## 🛠️ Arsitektur & Teknologi

```
                      E-Journal Skaduta
                             │
      ┌──────────────────────┴──────────────────────┐
      ▼                                             ▼
Android Native (app/)                        Web PWA (web/)
• Kotlin 2.2 / JVM 17                        • React 18 + Vite
• Jetpack Compose (Material 3)               • Tailwind CSS 3.4
• SQLite Room Database 2.6                   • IndexedDB + LocalStorage Sync
• Android Graphics PdfDocument               • jsPDF + jspdf-autotable
• In-App GitHub Auto-Updater                 • Service Worker (Network-First)
• Target: Android 15 (API 36)                • Hosting: GitHub Pages
```

---

## 💻 Panduan Pengembang (Build Lokal)

### Menjalankan Modul Android
1. Buka folder proyek ini di **Android Studio Ladybug** atau versi yang lebih baru.
2. Build APK rilis produksi:
   ```bash
   ./gradlew assembleRelease
   ```
   Berkas APK hasil build berada di: `app/build/outputs/apk/release/app-release.apk`.

### Menjalankan Modul Web PWA
1. Masuk ke direktori web:
   ```bash
   cd web
   ```
2. Pasang dependensi dan jalankan server pengembangan:
   ```bash
   npm install
   npm run dev
   ```
3. Build bundel produksi:
   ```bash
   npm run build
   ```

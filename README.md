# E-Journal Android Application 📓🏢

**E-Journal** adalah aplikasi Android Native modern yang dirancang khusus untuk mencatat kegiatan pekerjaan sehari-hari di kantor/instansi secara *offline-first* dan menghasilkan berkas cetak (*print out*) bulanan berupa dokumen **PDF A4 terstruktur** lengkap dengan kop surat dan kolom pengesahan tanda tangan.

---

## ✨ Fitur Utama

1. **Pencatatan Jurnal Harian (Daily Work Log)**:
   - Pemilihan Hari & Tanggal otomatis dengan kalender interaktif.
   - Kolom isian uraian deskripsi kegiatan kerja harian.
   - Lampiran bukti dukung fleksibel:
     - **Teks**: tautan Google Drive/dokumen, nomor surat dinas, atau ringkasan hasil.
     - **Foto/Media**: kamera langsung atau memilih dari galeri perangkat (tersimpan aman di penyimpanan internal aplikasi).
   - Pencarian cepat (*real-time search*) dan filter catatan pekerjaan.

2. **Ekspor & Cetak Laporan Bulanan (Monthly PDF Generator)**:
   - Pemilihan periode Bulan & Tahun yang fleksibel.
   - Otomatis meng-generate berkas PDF standar A4 (`595 x 842 pt`) dengan:
     - **Kop Surat**: Judul laporan, periode bulan/tahun, dan data lengkap pegawai.
     - **Tabel Rekapitulasi**: Kolom Nomor, Hari/Tanggal, Uraian Pekerjaan, dan Bukti Dukung (teks atau thumbnail gambar bukti kerja).
     - **Kolom Pengesahan**: Kota & tanggal penetapan, tanda tangan Pegawai Yang Bersangkutan, dan tanda tangan Mengetahui Atasan Langsung beserta NIP/Jabatan.
   - Tombol langsung untuk **Buka/Pratinjau PDF** dan **Bagikan** via WhatsApp, Email, atau Google Drive.

3. **Profil Pegawai & Atasan**:
   - Penyimpanan data Nama Lengkap, NIP/ID, Jabatan, Unit Kerja/Divisi.
   - Data Atasan Langsung (Nama, NIP, Jabatan) dan Kota Penetapan.
   - Terintegrasi otomatis ke setiap berkas PDF yang dicetak.

4. **Pengingat Harian (WorkManager Reminder)**:
   - Notifikasi otomatis menjelang jam kepulangan kantor (dapat diatur jam dan menitnya).
   - Pintar: Jika pengguna sudah mengisi jurnal pada hari tersebut, notifikasi tidak akan mengganggu.

5. **Privasi & Offline-First**:
   - Seluruh data tersimpan secara lokal di SQLite perangkat menggunakan Jetpack Room.
   - Tidak memerlukan koneksi internet untuk bekerja.

---

## 🛠️ Tech Stack & Arsitektur

- **Bahasa**: Kotlin 2.2
- **UI Framework**: Jetpack Compose dengan Material 3 Design
- **Arsitektur**: MVVM (Model-View-ViewModel) + Clean Architecture
- **Basis Data**: Room Database (SQLite) + Kotlin Coroutines & Flow
- **Background Tasks**: Android Jetpack WorkManager
- **PDF Engine**: Android Native `PdfDocument` & `StaticLayout` Table Drawing
- **Image Loading**: Coil Compose
- **Target SDK**: Android 15 (API 36) | **Min SDK**: Android 8.0 Oreo (API 26)

---

## 🚀 Cara Menjalankan & Build

### 1. Buka di Android Studio
Buka direktori ini di **Android Studio Ladybug** atau versi yang lebih baru. Gradle akan otomatis melakukan sinkronisasi.

### 2. Build Debug APK via Terminal
```bash
./gradlew assembleDebug
```
Berkas APK akan berada di: `app/build/outputs/apk/debug/app-debug.apk`.

### 3. Install langsung ke Device / Emulator
```bash
./gradlew installDebug
```


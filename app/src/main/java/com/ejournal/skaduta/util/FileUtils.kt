package com.ejournal.skaduta.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object FileUtils {

    private const val MAX_IMAGE_BYTES = 20L * 1024 * 1024 // 20 MB
    private const val MAX_IMAGE_DIMENSION = 12000 // Cegah decompression bomb
    private const val MAX_IMAGE_PIXELS = 50_000_000L // Maks 50 MP
    private const val TARGET_MAX_SIDE = 1600 // Ukuran optimal & bersih dari metadata EXIF

    fun saveUriToInternalStorage(context: Context, sourceUri: Uri): String? {
        return try {
            // Security: blokir URI skema file:// yang mengarah ke direktori internal aplikasi (cegah LFI/self-copy)
            if ("file".equals(sourceUri.scheme, ignoreCase = true)) {
                val path = sourceUri.path ?: return null
                val canonicalTarget = File(path).canonicalPath
                val appDataDir = File(context.applicationInfo.dataDir).canonicalPath
                val cameraCacheDir = File(context.cacheDir, "camera_images").canonicalPath
                if (canonicalTarget.startsWith(appDataDir) && !canonicalTarget.startsWith("$cameraCacheDir${File.separator}")) {
                    return null
                }
            }

            // Security: periksa MIME jika tersedia dari ContentResolver
            val mime = context.contentResolver.getType(sourceUri)
            if (mime != null && !mime.startsWith("image/")) return null

            val imagesDir = File(context.filesDir, "evidence_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }

            val stagingFile = File(imagesDir, "staging_${UUID.randomUUID()}.tmp")
            val destinationFile = File(imagesDir, "evidence_${UUID.randomUUID()}.jpg")

            try {
                val inputStream: InputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
                var copiedOk = false
                inputStream.use { input ->
                    FileOutputStream(stagingFile).use { output ->
                        val buf = ByteArray(8 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_IMAGE_BYTES) return null
                            output.write(buf, 0, n)
                        }
                        if (total <= 0L) return null
                    }
                    copiedOk = true
                }
                if (!copiedOk) return null

                if (!validateAndSanitizeImage(stagingFile, destinationFile)) {
                    destinationFile.delete()
                    return null
                }
                destinationFile.absolutePath
            } finally {
                stagingFile.delete()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Memvalidasi foto hasil kamera dari cache, membersihkan EXIF/payload dengan re-encode JPEG,
     * memindahkannya ke internal storage privat, dan menghapus file sementara di cache.
     */
    fun finalizeCapturedPhoto(context: Context, tempFile: File): String? {
        return try {
            if (!tempFile.exists() || tempFile.length() <= 0L || tempFile.length() > MAX_IMAGE_BYTES) {
                return null
            }
            val imagesDir = File(context.filesDir, "evidence_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()

            val destinationFile = File(imagesDir, "evidence_${UUID.randomUUID()}.jpg")
            if (!validateAndSanitizeImage(tempFile, destinationFile)) {
                destinationFile.delete()
                return null
            }
            destinationFile.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            try {
                tempFile.delete()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Memverifikasi magic bytes/dimensi gambar, mencegah Decompression Bomb (OOM),
     * dan melakukan re-encode ke JPEG bersih (menghapus metadata EXIF/GPS & payload tersembunyi).
     */
    private fun validateAndSanitizeImage(sourceFile: File, destFile: File): Boolean {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOptions)

        val w = boundsOptions.outWidth
        val h = boundsOptions.outHeight
        val outMime = boundsOptions.outMimeType

        if (w <= 0 || h <= 0 || outMime == null || !outMime.startsWith("image/")) {
            return false
        }
        if (w > MAX_IMAGE_DIMENSION || h > MAX_IMAGE_DIMENSION) {
            return false
        }
        if (w.toLong() * h.toLong() > MAX_IMAGE_PIXELS) {
            return false
        }

        var sampleSize = 1
        while (w / sampleSize > TARGET_MAX_SIDE || h / sampleSize > TARGET_MAX_SIDE) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions) ?: return false
        return try {
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Memastikan file bukti berada di dalam folder resmi aplikasi (mencegah Path Traversal).
     */
    fun isSafeEvidenceFile(context: Context, file: File): Boolean {
        return try {
            val canonical = file.canonicalPath
            val evidenceDir = File(context.filesDir, "evidence_images").canonicalPath
            val cameraCacheDir = File(context.cacheDir, "camera_images").canonicalPath
            canonical.startsWith("$evidenceDir${File.separator}") ||
                canonical.startsWith("$cameraCacheDir${File.separator}")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Menghapus file bukti foto secara aman hanya jika berada di dalam direktori bukti internal.
     */
    fun deleteEvidenceFile(context: Context, filePath: String?) {
        if (filePath.isNullOrBlank()) return
        try {
            val file = File(filePath)
            if (file.exists() && isSafeEvidenceFile(context, file)) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Membersihkan sisa file foto sementara di cache kamera.
     */
    fun cleanupTempCameraFiles(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "camera_images")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile) file.delete()
                }
            }
        } catch (_: Exception) {
        }
    }

    fun createTempImageUri(context: Context): Pair<File, Uri> {
        val cacheDir = File(context.cacheDir, "camera_images")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val tempFile = File.createTempFile("capture_", ".jpg", cacheDir)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return Pair(tempFile, uri)
    }

    fun savePdfToPublicDownloads(context: Context, sourceFile: File): String? {
        if (!sourceFile.exists() || !isSafeReportFile(context, sourceFile)) return null
        // Security: pastikan nama file aman dari karakter path traversal
        val fileName = sourceFile.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    try {
                        val outStream = resolver.openOutputStream(uri) ?: throw IllegalStateException("OutputStream null")
                        outStream.use { out ->
                            sourceFile.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    } catch (e: Exception) {
                        try {
                            resolver.delete(uri, null, null)
                        } catch (_: Exception) {
                        }
                        throw e
                    }
                }
                "Download/$fileName"
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, fileName)
                sourceFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf("application/pdf"), null)
                destFile.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isSafeReportFile(context: Context, file: File): Boolean {
        return try {
            val canonical = file.canonicalPath
            val reportsDir = File(context.filesDir, "generated_reports").canonicalPath
            canonical.startsWith("$reportsDir${File.separator}")
        } catch (_: Exception) {
            false
        }
    }

    fun openPdfFile(context: Context, file: File) {
        if (!file.exists() || !isSafeReportFile(context, file)) return
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Buka Berkas PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
        }
    }

    fun sharePdfFile(context: Context, file: File) {
        if (!file.exists() || !isSafeReportFile(context, file)) return
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Laporan E-Journal Bulanan")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Bagikan Laporan PDF")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
        }
    }
}

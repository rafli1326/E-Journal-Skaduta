package com.ejournal.skaduta.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkSize: Long
)

object AppUpdateManager {

    private const val GITHUB_OWNER = "rafli1326"
    private const val GITHUB_REPO = "E-Journal-Skaduta"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private const val MAX_JSON_RESPONSE_BYTES = 512 * 1024 // 512 KB
    private const val MAX_APK_DOWNLOAD_BYTES = 100L * 1024 * 1024 // 100 MB

    private val ALLOWED_UPDATE_HOSTS = setOf(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    /**
     * Memastikan URL unduhan hanya menggunakan protokol HTTPS dan berasal dari domain resmi GitHub.
     */
    private fun isTrustedUpdateUrl(urlStr: String): Boolean {
        return try {
            val url = URL(urlStr)
            if (!"https".equals(url.protocol, ignoreCase = true)) return false
            val host = url.host?.lowercase(Locale.ROOT) ?: return false
            ALLOWED_UPDATE_HOSTS.any { allowed ->
                host == allowed || host.endsWith(".$allowed")
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Membaca teks dari InputStream dengan batas maksimum byte untuk mencegah Memory Exhaustion (DoS).
     */
    private fun readBoundedText(input: InputStream, maxBytes: Int): String? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.1.0"
        } catch (_: Exception) {
            "1.1.0"
        }
    }

    fun checkForUpdate(context: Context, isManual: Boolean = false) {
        if (_isChecking.value || _isDownloading.value) return

        CoroutineScope(Dispatchers.IO).launch {
            _isChecking.value = true
            var connection: HttpsURLConnection? = null
            try {
                val currentVersion = getCurrentVersionName(context)
                val url = URL(LATEST_RELEASE_URL)
                connection = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "E-Journal-Skaduta-Android")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseStr = connection.inputStream.use { readBoundedText(it, MAX_JSON_RESPONSE_BYTES) }
                        ?: throw IllegalStateException("Respons server terlalu besar")
                    val json = JSONObject(responseStr)

                    val rawTag = json.optString("tag_name", "").removePrefix("v").removePrefix("V").trim()
                    val safeTag = rawTag.replace(Regex("[^0-9A-Za-z._-]"), "").take(32)
                    val releaseTitle = json.optString("name", "Pembaruan Aplikasi").take(200)
                    val releaseNotes = json.optString("body", "Peningkatan performa dan perbaikan sistem.").take(4000)

                    var apkUrl = ""
                    var apkSize = 0L

                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            val candidateUrl = asset.optString("browser_download_url", "")
                            if (name.endsWith(".apk", ignoreCase = true) && isTrustedUpdateUrl(candidateUrl)) {
                                apkUrl = candidateUrl
                                apkSize = asset.optLong("size", 0L).coerceAtLeast(0L)
                                break
                            }
                        }
                    }

                    val hasUpdate = safeTag.isNotEmpty() &&
                        isNewerVersion(currentVersion, safeTag) &&
                        apkUrl.isNotBlank() &&
                        apkSize <= MAX_APK_DOWNLOAD_BYTES

                    val info = UpdateInfo(
                        hasUpdate = hasUpdate,
                        latestVersion = safeTag,
                        currentVersion = currentVersion,
                        releaseTitle = releaseTitle,
                        releaseNotes = releaseNotes,
                        apkDownloadUrl = apkUrl,
                        apkSize = apkSize
                    )

                    withContext(Dispatchers.Main) {
                        _updateInfo.value = info
                        if (hasUpdate) {
                            _showUpdateDialog.value = true
                        } else if (isManual) {
                            _statusMessage.value = "Aplikasi Anda sudah versi terbaru (v$currentVersion)"
                        }
                    }
                } else if (isManual) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Gagal memeriksa pembaruan (Kode: ${connection.responseCode})"
                    }
                }
            } catch (_: Exception) {
                if (isManual) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Tidak dapat terhubung ke server pembaruan."
                    }
                }
            } finally {
                connection?.disconnect()
                _isChecking.value = false
            }
        }
    }

    /**
     * Membandingkan semantic versioning (misal 1.1.0 vs 1.0.0).
     * Mengembalikan true jika remoteVersion lebih tinggi dari currentVersion.
     */
    fun isNewerVersion(currentVersion: String, remoteVersion: String): Boolean {
        try {
            val currParts = currentVersion.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
            val remoteParts = remoteVersion.split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }

            val maxLen = maxOf(currParts.size, remoteParts.size)
            for (i in 0 until maxLen) {
                val c = currParts.getOrElse(i) { 0 }
                val r = remoteParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (_: Exception) {
            return false
        }
        return false
    }

    fun downloadAndInstall(context: Context, info: UpdateInfo) {
        if (_isDownloading.value || info.apkDownloadUrl.isBlank() || !isTrustedUpdateUrl(info.apkDownloadUrl)) return

        CoroutineScope(Dispatchers.IO).launch {
            _isDownloading.value = true
            _downloadProgress.value = 0f

            var stagingFile: File? = null
            var connection: HttpsURLConnection? = null
            try {
                val updatesDir = File(context.cacheDir, "apk_updates")
                if (!updatesDir.exists()) {
                    updatesDir.mkdirs()
                }

                // Bersihkan unduhan lama
                updatesDir.listFiles()?.forEach { it.delete() }

                val safeVersion = info.latestVersion.replace(Regex("[^0-9A-Za-z._-]"), "_").take(32).ifBlank { "latest" }
                val targetApk = File(updatesDir, "E-Journal-Skaduta-v$safeVersion.apk")
                stagingFile = File(updatesDir, "download.tmp")

                var currentUrlStr = info.apkDownloadUrl
                var redirectCount = 0

                while (true) {
                    if (!isTrustedUpdateUrl(currentUrlStr)) {
                        throw SecurityException("URL pengunduhan tidak terpercaya")
                    }
                    connection = (URL(currentUrlStr).openConnection() as HttpsURLConnection).apply {
                        requestMethod = "GET"
                        instanceFollowRedirects = false
                        connectTimeout = 15000
                        readTimeout = 15000
                        setRequestProperty("User-Agent", "E-Journal-Skaduta-Android")
                    }

                    val code = connection.responseCode
                    if (code in 300..399 && redirectCount < 5) {
                        val location = connection.getHeaderField("Location")
                            ?: throw SecurityException("Header redirect tidak valid")
                        val resolvedUrl = URL(URL(currentUrlStr), location).toString()
                        connection.disconnect()
                        currentUrlStr = resolvedUrl
                        redirectCount++
                    } else {
                        break
                    }
                }

                val finalConn = connection ?: throw IllegalStateException("Koneksi gagal")
                if (finalConn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Server mengembalikan kode ${finalConn.responseCode}")
                }

                val totalBytes = finalConn.contentLengthLong.let { if (it <= 0) info.apkSize else it }
                if (totalBytes > MAX_APK_DOWNLOAD_BYTES) {
                    throw SecurityException("Ukuran berkas APK melebihi batas aman")
                }

                var downloadedBytes = 0L

                finalConn.inputStream.use { input: InputStream ->
                    FileOutputStream(stagingFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            downloadedBytes += bytesRead
                            if (downloadedBytes > MAX_APK_DOWNLOAD_BYTES) {
                                throw SecurityException("Ukuran unduhan melebihi batas aman")
                            }
                            output.write(buffer, 0, bytesRead)
                            if (totalBytes > 0) {
                                val prog = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                _downloadProgress.value = prog
                            }
                        }
                        output.flush()
                    }
                }

                if (downloadedBytes <= 0L || !stagingFile.renameTo(targetApk)) {
                    throw IllegalStateException("Gagal menyimpan berkas pembaruan")
                }

                // Security: verifikasi bahwa file yang diunduh benar-benar APK untuk package ini
                if (!verifyDownloadedApk(context, targetApk)) {
                    targetApk.delete()
                    throw SecurityException("Berkas APK tidak valid atau bukan milik aplikasi ini")
                }

                withContext(Dispatchers.Main) {
                    _isDownloading.value = false
                    _showUpdateDialog.value = false
                    installApk(context, targetApk)
                }
            } catch (e: Exception) {
                stagingFile?.delete()
                withContext(Dispatchers.Main) {
                    _isDownloading.value = false
                    _statusMessage.value = "Gagal mengunduh berkas pembaruan."
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun isSafeApkFile(context: Context, file: File): Boolean {
        return try {
            val canonical = file.canonicalPath
            val updatesDir = File(context.cacheDir, "apk_updates").canonicalPath
            canonical.startsWith("$updatesDir${File.separator}") && canonical.endsWith(".apk", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun verifyDownloadedApk(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists() || !isSafeApkFile(context, apkFile)) return false
            val pm = context.packageManager
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
            archiveInfo != null && archiveInfo.packageName == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists() || !isSafeApkFile(context, apkFile)) return

        // Pada Android 8.0+ (Oreo / API 26+), periksa izin pasang aplikasi dari sumber ini
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
    }
}

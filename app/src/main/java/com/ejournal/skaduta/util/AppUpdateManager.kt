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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

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
            try {
                val currentVersion = getCurrentVersionName(context)
                val url = URL(LATEST_RELEASE_URL)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "E-Journal-Skaduta-Android")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)

                    val tagName = json.optString("tag_name", "").removePrefix("v").removePrefix("V").trim()
                    val releaseTitle = json.optString("name", "Pembaruan Aplikasi")
                    val releaseNotes = json.optString("body", "Peningkatan performa dan perbaikan sistem.")

                    var apkUrl = ""
                    var apkSize = 0L

                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", "")
                                apkSize = asset.optLong("size", 0L)
                                break
                            }
                        }
                    }

                    val hasUpdate = isNewerVersion(currentVersion, tagName) && apkUrl.isNotBlank()
                    val info = UpdateInfo(
                        hasUpdate = hasUpdate,
                        latestVersion = tagName,
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
            } catch (e: Exception) {
                if (isManual) {
                    withContext(Dispatchers.Main) {
                        _statusMessage.value = "Tidak dapat terhubung ke server pembaruan."
                    }
                }
            } finally {
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
        if (_isDownloading.value || info.apkDownloadUrl.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            _isDownloading.value = true
            _downloadProgress.value = 0f

            var stagingFile: File? = null
            try {
                val updatesDir = File(context.cacheDir, "apk_updates")
                if (!updatesDir.exists()) {
                    updatesDir.mkdirs()
                }

                // Bersihkan unduhan lama
                updatesDir.listFiles()?.forEach { it.delete() }

                val targetApk = File(updatesDir, "E-Journal-Skaduta-v${info.latestVersion}.apk")
                stagingFile = File(updatesDir, "download.tmp")

                val url = URL(info.apkDownloadUrl)
                var connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "E-Journal-Skaduta-Android")
                }

                // Handle redirect (GitHub release download url redirects to AWS S3)
                var redirectCount = 0
                while (connection.responseCode in 300..399 && redirectCount < 5) {
                    val newUrl = connection.getHeaderField("Location") ?: break
                    connection.disconnect()
                    connection = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = true
                        connectTimeout = 15000
                        readTimeout = 15000
                    }
                    redirectCount++
                }

                val totalBytes = connection.contentLengthLong.let { if (it <= 0) info.apkSize else it }
                var downloadedBytes = 0L

                connection.inputStream.use { input: InputStream ->
                    FileOutputStream(stagingFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val prog = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                _downloadProgress.value = prog
                            }
                        }
                        output.flush()
                    }
                }

                stagingFile.renameTo(targetApk)

                withContext(Dispatchers.Main) {
                    _isDownloading.value = false
                    _showUpdateDialog.value = false
                    installApk(context, targetApk)
                }
            } catch (e: Exception) {
                stagingFile?.delete()
                withContext(Dispatchers.Main) {
                    _isDownloading.value = false
                    _statusMessage.value = "Gagal mengunduh berkas pembaruan: ${e.message}"
                }
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

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


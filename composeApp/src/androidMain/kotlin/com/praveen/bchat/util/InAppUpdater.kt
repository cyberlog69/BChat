package com.praveen.bchat.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSize: Long,
    val publishedAt: String
)

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class UpdateAvailable(val releaseInfo: AppReleaseInfo) : UpdateState
    data class Downloading(val progressFraction: Float, val bytesDownloaded: Long, val totalBytes: Long) : UpdateState
    data class ReadyToInstall(val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * In-App Self-Updater for BChat using GitHub Releases API.
 * Automatically checks, downloads, and launches seamless APK installation on Android.
 */
class InAppUpdater(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    companion object {
        private const val TAG = "InAppUpdater"
        private const val GITHUB_REPO = "cyberlog69/BChat"
        private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

        @Volatile
        private var INSTANCE: InAppUpdater? = null

        fun getInstance(context: Context): InAppUpdater {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InAppUpdater(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun checkForUpdate(manualTrigger: Boolean = false) {
        if (_updateState.value is UpdateState.Checking || _updateState.value is UpdateState.Downloading) return

        _updateState.value = UpdateState.Checking

        scope.launch(Dispatchers.IO) {
            try {
                val currentVersion = getCurrentVersionName()
                val url = URL(RELEASES_API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "BChat-Android-App")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val code = conn.responseCode
                if (code == 200) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseStr)

                    val tagName = json.optString("tag_name", "")
                    val releaseTitle = json.optString("name", "New Release")
                    val releaseNotes = json.optString("body", "Bug fixes and performance improvements.")
                    val publishedAt = json.optString("published_at", "")

                    val cleanRemoteVersion = tagName.trimStart('v', 'V')
                    val assets = json.optJSONArray("assets")

                    var downloadUrl: String? = null
                    var fileName = "BChat-update.apk"
                    var fileSize = 0L

                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val aName = asset.optString("name", "")
                            if (aName.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url")
                                fileName = aName
                                fileSize = asset.optLong("size", 0L)
                                break
                            }
                        }
                    }

                    if (downloadUrl != null && isNewerVersion(cleanRemoteVersion, currentVersion)) {
                        val releaseInfo = AppReleaseInfo(
                            tagName = tagName,
                            versionName = cleanRemoteVersion,
                            releaseTitle = releaseTitle,
                            releaseNotes = releaseNotes,
                            downloadUrl = downloadUrl,
                            fileName = fileName,
                            fileSize = fileSize,
                            publishedAt = publishedAt
                        )
                        _updateState.value = UpdateState.UpdateAvailable(releaseInfo)
                        Log.d(TAG, "New version available: $cleanRemoteVersion (current: $currentVersion)")
                    } else {
                        _updateState.value = UpdateState.UpToDate(currentVersion)
                        Log.d(TAG, "App is up to date: $currentVersion")
                    }
                } else if (code == 404) {
                    // No releases published yet on GitHub repo
                    _updateState.value = UpdateState.UpToDate(currentVersion)
                } else {
                    _updateState.value = UpdateState.Error("Server returned code $code")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "Failed to check for updates")
            }
        }
    }

    fun startDownload(releaseInfo: AppReleaseInfo) {
        if (_updateState.value is UpdateState.Downloading) return

        _updateState.value = UpdateState.Downloading(0f, 0L, releaseInfo.fileSize)

        scope.launch(Dispatchers.IO) {
            try {
                val updatesDir = File(context.externalCacheDir ?: context.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val targetApk = File(updatesDir, releaseInfo.fileName)
                if (targetApk.exists()) targetApk.delete()

                val url = URL(releaseInfo.downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.connect()

                val totalLength = if (conn.contentLengthLong > 0) conn.contentLengthLong else releaseInfo.fileSize
                val input = BufferedInputStream(conn.inputStream)
                val output = FileOutputStream(targetApk)

                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                var downloaded = 0L
                var lastEmitTime = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime > 150 || downloaded == totalLength) {
                        lastEmitTime = now
                        val fraction = if (totalLength > 0) downloaded.toFloat() / totalLength.toFloat() else 0f
                        _updateState.value = UpdateState.Downloading(fraction.coerceIn(0f, 1f), downloaded, totalLength)
                    }
                }

                output.flush()
                output.close()
                input.close()

                _updateState.value = UpdateState.ReadyToInstall(targetApk)
                Log.d(TAG, "Downloaded update successfully to ${targetApk.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed downloading update", e)
                _updateState.value = UpdateState.Error(e.localizedMessage ?: "Download failed")
            }
        }
    }

    fun installApk(apkFile: File) {
        try {
            if (!apkFile.exists()) {
                _updateState.value = UpdateState.Error("APK file not found")
                return
            }

            // Check Unknown Sources Permission for Android 8.0+ (Oreo)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            _updateState.value = UpdateState.Error("Failed to launch installer: ${e.message}")
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = local.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLength) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}

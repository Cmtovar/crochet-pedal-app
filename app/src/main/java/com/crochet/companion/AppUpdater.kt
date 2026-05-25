package com.crochet.companion

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    val name: String?,
    val assets: List<GitHubAsset>
)

data class GitHubAsset(
    val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String
)

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseName: String
)

class AppUpdater(private val githubRepo: String) {

    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$githubRepo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val release = gson.fromJson(response.body?.string(), GitHubRelease::class.java)
                ?: return@withContext null

            val latestVersion = release.tagName
                .removePrefix("v")
                .substringBefore("-build")

            if (!isNewer(latestVersion, currentVersion)) return@withContext null

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: return@withContext null

            UpdateInfo(
                latestVersion = release.tagName,
                downloadUrl = apkAsset.downloadUrl,
                releaseName = release.name ?: release.tagName
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadAndInstall(context: Context, updateInfo: UpdateInfo) = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, "updates")
        updateDir.mkdirs()
        updateDir.listFiles()?.forEach { it.delete() }

        val apkFile = File(updateDir, "update.apk")

        val request = Request.Builder().url(updateInfo.downloadUrl).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext

        response.body?.byteStream()?.use { input ->
            apkFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}

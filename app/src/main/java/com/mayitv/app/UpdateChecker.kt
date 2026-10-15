package com.mayitv.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/** 应用内更新：查询 GitHub 最新 Release，下载 APK。私有仓库时查询返回 null（由调用方回退浏览器）。 */
object UpdateChecker {

    private const val REPO = "madeincs/mayitv"
    private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases/latest"
    const val webUrl = "https://github.com/$REPO/releases/latest"

    data class Release(val versionCode: Int, val apkUrl: String, val name: String)

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        try {
            val json = Net.get(RELEASES_URL) ?: return@withContext null
            val obj = JSONObject(json)
            val vc = obj.optString("tag_name").removePrefix("v").toIntOrNull()
                ?: return@withContext null
            val asset = obj.optJSONArray("assets")?.optJSONObject(0) ?: return@withContext null
            val url = asset.optString("browser_download_url")
            if (url.isBlank()) null else Release(vc, url, asset.optString("name"))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun download(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url)
                .header("User-Agent", SiteConfig.UA).build()
            Net.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val dir = File(context.getExternalFilesDir(null), "update").apply { mkdirs() }
                val file = File(dir, "update.apk")
                resp.body?.byteStream()?.use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                if (file.exists() && file.length() > 0) file else null
            }
        } catch (e: Exception) {
            null
        }
    }
}

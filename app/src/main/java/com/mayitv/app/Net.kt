package com.mayitv.app

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Net {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** 同步 GET，返回响应体字符串；失败返回 null */
    fun get(url: String, referer: String? = null): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", SiteConfig.UA)
                .apply { if (referer != null) header("Referer", referer) }
                .build()
            client.newCall(req).execute().use { resp ->
                L.d("GET ${resp.code} $url")
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            L.e("GET fail $url : ${e.message}", e)
            null
        }
    }
}

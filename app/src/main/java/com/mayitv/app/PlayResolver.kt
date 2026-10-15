package com.mayitv.app

import org.json.JSONObject

/**
 * 播放解析：从播放页 -> player_data -> 解析页 -> 真实 m3u8
 *
 * 链路：
 * 1) 播放页 /vodplay/{id}-{源}-{集}.html 中含 var player_data={...}
 * 2) player_data.from 决定播放器 JS：/static/player/{from}.js，其内含 iframe 解析地址
 * 3) 访问解析地址 ?url=<encoded>，页面内 var video_url='...m3u8' 即真实播放地址
 */
object PlayResolver {

    data class PlayInfo(
        val m3u8: String?,
        val nextLink: String?,
        val prevLink: String?,
        val from: String
    )

    private val PLAYER_DATA_RE =
        Regex("""var\s+player_data\s*=\s*(\{.*?\})""", RegexOption.DOT_MATCHES_ALL)
    private val SRC_RE = Regex("""src=["']([^"']+\?url=)""")
    private val VIDEO_URL_RE = Regex("""var\s+video_url\s*=\s*['"](.*?)['"]""")

    fun resolve(playUrl: String): PlayInfo {
        val full = if (playUrl.startsWith("http")) playUrl else SiteConfig.SITE + playUrl
        L.d("resolve: playPage=$full")

        // 1) 播放页
        val html = Net.get(full, SiteConfig.SITE)
            ?: run { L.e("resolve: 播放页无响应"); return PlayInfo(null, null, null, "") }

        // 2) player_data
        val jsonStr = PLAYER_DATA_RE.find(html)?.groupValues?.getOrNull(1)
            ?: run { L.e("resolve: 未找到 player_data"); return PlayInfo(null, null, null, "") }
        val data = JSONObject(jsonStr)
        val url = data.optString("url")
        val from = data.optString("from")
        val next = data.optString("link_next").takeIf { it.isNotBlank() }
        val prev = data.optString("link_pre").takeIf { it.isNotBlank() }
        L.d("resolve: from=$from url=$url next=${next != null} prev=${prev != null}")

        // 3) 解析地址：优先从播放器 JS 提取，失败用默认
        var parseBase = SiteConfig.PARSE_API
        val playerJs = Net.get("${SiteConfig.SITE}/static/player/$from.js", SiteConfig.SITE)
        if (playerJs != null) {
            SRC_RE.find(playerJs)?.groupValues?.getOrNull(1)?.let {
                parseBase = it
                L.d("resolve: 从 ${from}.js 提取 parseBase=$it")
            }
        } else {
            L.w("resolve: ${from}.js 无响应，用默认 PARSE_API")
        }

        // 4) 解析页
        val parseUrl = parseBase + url
        L.d("resolve: parseUrl=$parseUrl")
        val parseHtml = Net.get(parseUrl, SiteConfig.SITE)
            ?: run { L.e("resolve: 解析页无响应"); return PlayInfo(null, next, prev, from) }

        // 5) 真实 m3u8
        val m3u8 = VIDEO_URL_RE.find(parseHtml)?.groupValues?.getOrNull(1)
        if (m3u8 == null) L.e("resolve: 解析页未提取到 video_url") else L.d("resolve: m3u8=$m3u8")
        return PlayInfo(m3u8, next, prev, from)
    }
}

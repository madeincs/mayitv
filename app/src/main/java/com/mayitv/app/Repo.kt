package com.mayitv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** 数据仓库：异步抓取站点页面并解析 */
object Repo {

    suspend fun home(): List<Vod> = withContext(Dispatchers.IO) {
        Net.get("${SiteConfig.SITE}/")?.let { Parser.parseList(it) } ?: emptyList()
    }

    suspend fun category(type: Int, page: Int): List<Vod> = withContext(Dispatchers.IO) {
        Net.get("${SiteConfig.SITE}/vodtype/$type-$page.html")?.let { Parser.parseList(it) } ?: emptyList()
    }

    suspend fun search(wd: String, page: Int): List<Vod> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(wd, "UTF-8")
        Net.get("${SiteConfig.SITE}/vodsearch/-------------.html?wd=$q&page=$page")
            ?.let { Parser.parseList(it) } ?: emptyList()
    }

    suspend fun detail(id: String): VodDetail? = withContext(Dispatchers.IO) {
        Net.get("${SiteConfig.SITE}/voddetail/$id.html")?.let { Parser.parseDetail(it, id) }
    }
}

package com.mayitv.app

import org.jsoup.Jsoup

/** 用 Jsoup 解析站点 HTML（API 已关闭，走页面解析） */
object Parser {

    private const val BASE = SiteConfig.SITE
    private val ID_RE = Regex("""/voddetail/(\d+)""")

    /** 首页/分类/搜索的影片列表（共用 stui 模板的 .stui-vodlist__box） */
    fun parseList(html: String): List<Vod> {
        val doc = Jsoup.parse(html, BASE)
        return doc.select(".stui-vodlist__box").map { box ->
            val a = box.selectFirst(".stui-vodlist__thumb")
            val href = a?.absUrl("href") ?: ""
            val id = ID_RE.find(href)?.groupValues?.getOrNull(1) ?: ""
            val title = a?.attr("title")?.takeIf { it.isNotBlank() }
                ?: box.selectFirst(".title a")?.text() ?: ""
            val cover = a?.absUrl("data-original")?.takeIf { it.isNotBlank() }
                ?: a?.absUrl("src") ?: ""
            val remark = box.selectFirst(".pic-text")?.text() ?: ""
            Vod(id, title, cover, remark)
        }
    }

    /** 详情页：信息 + 多播放源 + 选集 */
    fun parseDetail(html: String, id: String): VodDetail? {
        val doc = Jsoup.parse(html, BASE)
        val title = doc.selectFirst(".stui-content__detail h1.title")?.text()
            ?: doc.selectFirst(".stui-content h1.title")?.text() ?: return null
        val cover = doc.selectFirst(".stui-content__thumb img")?.absUrl("data-original")
            ?.takeIf { it.isNotBlank() } ?: doc.selectFirst(".stui-content__thumb img")?.absUrl("src") ?: ""
        val info = doc.select(".stui-content__detail .data").eachText().joinToString("\n")
        val desc = doc.selectFirst(".detail-content")?.text()
            ?: doc.selectFirst(".detail-sketch")?.text() ?: ""

        val sources = mutableListOf<PlaySource>()
        val tabs = doc.select(".play_source .title")
        for (tab in tabs) {
            val mid = tab.attr("data-mid")
            val name = tab.text().ifBlank { "源$mid" }
            val eps = doc.select(".play_source_list_item[data-mid=$mid] a").mapNotNull { e ->
                val url = e.absUrl("href")
                if (url.isNotBlank()) Episode(e.text().ifBlank { "播放" }, url) else null
            }
            if (eps.isNotEmpty()) sources.add(PlaySource(name, eps))
        }
        return VodDetail(id, title, cover, info, desc, sources)
    }
}

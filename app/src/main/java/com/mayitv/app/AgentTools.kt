package com.mayitv.app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

/**
 * 工具白名单 + 执行器。
 * 工具是 Agent 唯一能触达业务的能力边界（搜索/分类/详情/播放），
 * 全部复用现有 Repo / PlayResolver / PlayerActivity 链路，不改动原逻辑。
 */
object AgentTools {
    val specs: List<ToolSpec> = listOf(
        ToolSpec("home", "返回影视库首页/热门推荐"),
        ToolSpec("search", "按关键词搜索影片", mapOf("wd" to "搜索关键词")),
        ToolSpec("category", "按分类浏览", mapOf("type" to "0首页/1电影/2连续剧/3综艺/4动漫")),
        ToolSpec("detail", "查看影片详情", mapOf("id" to "影片id")),
        ToolSpec("play", "直接播放影片", mapOf("id" to "影片id")),
        ToolSpec("help", "显示可用能力说明")
    )
}

/** 工具执行器：把白名单工具调用映射到真实业务 */
class ToolExecutor(private val activity: AppCompatActivity) {

    suspend fun execute(call: LlmToolCall): ToolResult = when (call.name) {
        "home" -> home()
        "search" -> search(call.arg("wd"))
        "category" -> category(call.arg("type"))
        "detail" -> detail(call.arg("id"))
        "play" -> play(call.arg("id"))
        "help" -> help()
        else -> ToolResult(false, "未知工具: ${call.name}")
    }

    private fun LlmToolCall.arg(k: String): String = args[k] ?: ""

    private suspend fun home(): ToolResult {
        val list = Repo.home()
        return if (list.isEmpty()) ToolResult(false, "首页加载失败，请稍后重试")
        else ToolResult(true, "为你返回了 ${list.size} 部热门影片。", UiBlock.Cards("热门推荐", list))
    }

    private suspend fun search(wd: String): ToolResult {
        if (wd.isBlank()) return ToolResult(false, "请输入要搜索的关键词")
        val list = Repo.search(wd, 1)
        return if (list.isEmpty()) ToolResult(false, "没有找到「$wd」相关内容")
        else ToolResult(true, "搜索「$wd」，找到 ${list.size} 个结果。", UiBlock.Cards("搜索结果 · $wd", list))
    }

    private suspend fun category(type: String): ToolResult {
        val t = type.toIntOrNull() ?: 1
        if (t == 0) return home()
        val name = when (t) { 1 -> "电影"; 2 -> "连续剧"; 3 -> "综艺"; 4 -> "动漫"; else -> "分类$t" }
        val list = Repo.category(t, 1)
        return if (list.isEmpty()) ToolResult(false, "$name 分类加载失败")
        else ToolResult(true, "为你浏览$name。", UiBlock.Cards(name, list))
    }

    private suspend fun detail(id: String): ToolResult {
        val d = Repo.detail(id) ?: return ToolResult(false, "详情加载失败 id=$id")
        val chips = listOf(ActionChip("▶ 立即播放", "play", mapOf("id" to d.id)))
        return ToolResult(true, "${d.title}\n${d.info}", UiBlock.Actions("操作", chips))
    }

    private suspend fun play(id: String): ToolResult {
        val d = Repo.detail(id) ?: return ToolResult(false, "播放失败：找不到影片 $id")
        val src = d.sources.firstOrNull() ?: return ToolResult(false, "「${d.title}」暂无可播放源")
        val ep = src.episodes.firstOrNull() ?: return ToolResult(false, "「${d.title}」暂无可播放源")
        L.d("Agent: play ${d.title} src=${src.name} ep=${ep.name}")
        activity.startActivity(
            Intent(activity, PlayerActivity::class.java)
                .putExtra("url", ep.playUrl)
                .putExtra("title", "${d.title} · ${ep.name}")
        )
        return ToolResult(true, "正在播放「${d.title} · ${ep.name}」…")
    }

    private fun help(): ToolResult {
        val chips = listOf(
            ActionChip("🏠 首页推荐", "home"),
            ActionChip("🎬 电影", "category", mapOf("type" to "1")),
            ActionChip("📺 连续剧", "category", mapOf("type" to "2")),
            ActionChip("🎤 综艺", "category", mapOf("type" to "3")),
            ActionChip("🌸 动漫", "category", mapOf("type" to "4"))
        )
        return ToolResult(
            true,
            "我可以帮你搜索、分类浏览、查看详情并直接播放影片。试试下面的快捷指令：",
            UiBlock.Actions("快捷指令", chips)
        )
    }
}

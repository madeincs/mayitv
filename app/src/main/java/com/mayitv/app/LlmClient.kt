package com.mayitv.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * LLM 客户端接口。
 * 两种实现：
 *  - [DemoLlmClient]：离线意图解析（无需密钥），让整条链路开箱即用。
 *  - [SenseNovaLlmClient]：真实 LLM（OpenAI 兼容 tool calling），配置密钥后启用。
 */
interface LlmClient {
    suspend fun complete(messages: List<LlmMessage>): LlmReply
}

/**
 * 离线演示客户端：本地关键词意图解析 -> 产出工具调用。
 * 用于无密钥时可完整演示“对话 -> 工具 -> 动态 UI -> 播放”闭环。
 */
class DemoLlmClient : LlmClient {

    override suspend fun complete(messages: List<LlmMessage>): LlmReply {
        delay(300)
        // 若上一轮已执行工具，则本轮只收尾，避免死循环
        if (messages.any { it.role == "tool" }) {
            return LlmReply("以上结果可以点击播放，或者告诉我你接下来想看什么。")
        }
        val last = messages.lastOrNull { it.role == "user" }?.content?.trim().orEmpty()
        if (last.isBlank()) return LlmReply("你好！我是蚂蚁影视助手，试试说“搜索 XXX”或“电影”。")

        return when {
            last.contains("帮助") || last.contains("help") || last.contains("你能") ->
                LlmReply("好的，为你列出可用指令。", listOf(LlmToolCall("t1", "help")))
            last.contains("电影") ->
                LlmReply("正在为你浏览电影…", listOf(LlmToolCall("t1", "category", mapOf("type" to "1"))))
            last.contains("连续剧") || last.contains("电视剧") || last.contains("剧集") ->
                LlmReply("正在为你浏览连续剧…", listOf(LlmToolCall("t1", "category", mapOf("type" to "2"))))
            last.contains("综艺") ->
                LlmReply("正在为你浏览综艺…", listOf(LlmToolCall("t1", "category", mapOf("type" to "3"))))
            last.contains("动漫") || last.contains("动画") ->
                LlmReply("正在为你浏览动漫…", listOf(LlmToolCall("t1", "category", mapOf("type" to "4"))))
            last.contains("首页") || last.contains("推荐") || last.contains("热门") ->
                LlmReply("正在为你加载首页推荐…", listOf(LlmToolCall("t1", "home")))
            else -> {
                val kw = KEYWORDS.firstOrNull { last.contains(it) }
                    ?.let { last.substringAfter(it).trim() } ?: last
                if (kw.isBlank()) LlmReply("你可以说：搜索xxx / 电影 / 连续剧 / 动漫 / 首页 / 帮助")
                else LlmReply("正在为你搜索「$kw」…", listOf(LlmToolCall("t1", "search", mapOf("wd" to kw))))
            }
        }
    }

    companion object {
        private val KEYWORDS = listOf("帮我搜索", "搜索", "查找", "找一下", "找", "查询", "看看", "播放", "看", "推荐")
    }
}

/**
 * 真实 LLM 客户端：SenseNova（OpenAI 兼容 /chat/completions，含 function calling）。
 * 密钥与地址从 gradle 属性注入 BuildConfig，不写入源码/仓库。
 */
class SenseNovaLlmClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String
) : LlmClient {

    override suspend fun complete(messages: List<LlmMessage>): LlmReply =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("model", model)
                put("messages", messages.toJson())
                put("tools", AgentTools.specs.toToolsJson())
            }
            val req = okhttp3.Request.Builder()
                .url("$baseUrl/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            L.d("Llm: POST $baseUrl/chat/completions model=$model")
            val resp = Net.client.newCall(req).execute()
            val text = resp.use { r ->
                if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}: ${r.body?.string()}")
                r.body?.string().orEmpty()
            }
            parseReply(text)
        }

    private fun parseReply(text: String): LlmReply {
        val msg = JSONObject(text).getJSONArray("choices")
            .getJSONObject(0).getJSONObject("message")
        val content = msg.optString("content").takeIf { it.isNotBlank() }
        val calls = msg.optJSONArray("tool_calls")
        if (calls != null && calls.length() > 0) {
            val list = mutableListOf<LlmToolCall>()
            for (i in 0 until calls.length()) {
                val c = calls.getJSONObject(i)
                val fn = c.getJSONObject("function")
                val args = try {
                    val o = JSONObject(fn.optString("arguments"))
                    val m = mutableMapOf<String, String>()
                    val it = o.keys()
                    while (it.hasNext()) { val k = it.next(); m[k] = o.optString(k) }
                    m
                } catch (e: Exception) { emptyMap() }
                list.add(LlmToolCall(c.optString("id"), fn.optString("name"), args))
            }
            return LlmReply(text = content ?: "", toolCalls = list)
        }
        return LlmReply(text = content ?: "（空回复）")
    }

    private fun List<LlmMessage>.toJson(): JSONArray = JSONArray().apply {
        for (m in this@toJson) {
            put(JSONObject().apply {
                put("role", m.role)
                when (m.role) {
                    "tool" -> {
                        put("tool_call_id", m.toolCallId)
                        put("content", m.content)
                    }
                    else -> {
                        put("content", m.content)
                        if (m.toolCalls.isNotEmpty()) {
                            put("tool_calls", JSONArray().apply {
                                for (tc in m.toolCalls) {
                                    put(JSONObject().apply {
                                        put("id", tc.id)
                                        put("type", "function")
                                        put("function", JSONObject().apply {
                                            put("name", tc.name)
                                            put("arguments", JSONObject(tc.args).toString())
                                        })
                                    })
                                }
                            })
                        }
                    }
                }
            })
        }
    }

    private fun List<ToolSpec>.toToolsJson(): JSONArray = JSONArray().apply {
        for (t in this@toToolsJson) {
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", t.name)
                    put("description", t.description)
                    put("parameters", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            for ((k, v) in t.params) {
                                put(k, JSONObject().apply { put("type", "string"); put("description", v) })
                            }
                        })
                    })
                })
            })
        }
    }
}

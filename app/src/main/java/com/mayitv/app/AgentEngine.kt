package com.mayitv.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Agent 内核（不可变大主体）。
 *
 * - 系统提示词 / 工具白名单 / 对话状态机 固定，随 APK 版本更新，运行时 UI 无法修改。
 * - 对外只暴露 [state]（StateFlow），UI 是其纯投影；用户输入经 [submit] 回流。
 * - 流程：用户输入 -> LLM(可选工具) -> 执行白名单工具 -> 新状态 -> UI 重渲染。
 */
class AgentEngine(
    private val scope: CoroutineScope,
    private val executor: ToolExecutor,
    llm: LlmClient? = null
) {

    private val llm: LlmClient = llm ?: if (BuildConfig.LLM_API_KEY.isNotBlank()) {
        L.d("Agent: 使用 SenseNova LLM model=${BuildConfig.LLM_MODEL}")
        SenseNovaLlmClient(BuildConfig.LLM_BASE_URL, BuildConfig.LLM_API_KEY, BuildConfig.LLM_MODEL)
    } else {
        L.d("Agent: 未配置 LLM 密钥，使用离线 Demo 客户端")
        DemoLlmClient()
    }

    private val _state = MutableStateFlow(
        AgentUiState(
            messages = listOf(AgentMessage("assistant", WELCOME)),
            nav = buildNav()
        )
    )
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    /** 动态生成的应用导航：由 agent 产出的白名单入口（后续可让 LLM 增删） */
    private fun buildNav(): UiBlock.Nav = UiBlock.Nav(
        "影视 · 动态导航",
        listOf(
            NavItem("🏠", "首页推荐", "热门榜单", "home"),
            NavItem("🎬", "电影", "海量大片", "category", mapOf("type" to "1")),
            NavItem("📺", "连续剧", "追剧不停", "category", mapOf("type" to "2")),
            NavItem("🎤", "综艺", "欢乐现场", "category", mapOf("type" to "3")),
            NavItem("🌸", "动漫", "二次元世界", "category", mapOf("type" to "4")),
            NavItem("🔍", "搜索", "关键词找片", "__search")
        )
    )

    /** 喂给 LLM 的对话历史（OpenAI 兼容形状） */
    private val llmMessages = mutableListOf<LlmMessage>()

    /** 自由文本输入（经 LLM 决策） */
    fun submit(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _state.value.typing) return
        _state.value = _state.value.copy(messages = _state.value.messages + AgentMessage("user", text))
        llmMessages += LlmMessage("user", text)
        scope.launch { runLoop() }
    }

    /** UI 芯片/卡片触发的白名单工具调用（确定性，不经 LLM 决策） */
    fun runTool(name: String, args: Map<String, String>) {
        if (_state.value.typing) return
        scope.launch { executeAndEmit(LlmToolCall("ui-${System.nanoTime()}", name, args)) }
    }

    private suspend fun runLoop() {
        _state.value = _state.value.copy(typing = true, error = null)
        try {
            for (round in 0 until MAX_ROUNDS) {
                val reply = llm.complete(llmMessages)
                if (reply.toolCalls.isEmpty()) {
                    appendAssistant(reply.text)
                    break
                }
                // LLM 决定调用工具：回喂 assistant(tool_calls)，再执行并回喂结果
                llmMessages += LlmMessage("assistant", reply.text, toolCalls = reply.toolCalls)
                var projected = false
                for (call in reply.toolCalls) {
                    val result = executor.execute(call)
                    llmMessages += LlmMessage("tool", result.text, toolCallId = call.id)
                    if (!projected && result.ui != null) {
                        appendAssistant(reply.text.ifBlank { result.text }, result.ui)
                        projected = true
                    }
                }
                if (!projected) appendAssistant(reply.text.ifBlank { "完成。" })
            }
        } catch (e: Exception) {
            L.e("Agent: runLoop fail", e)
            appendAssistant("出错了：${e.message ?: "请稍后重试"}")
        } finally {
            _state.value = _state.value.copy(typing = false)
        }
    }

    /** 直接执行单个工具并投影结果（用于 UI 芯片点击） */
    private suspend fun executeAndEmit(call: LlmToolCall) {
        _state.value = _state.value.copy(typing = true, error = null)
        try {
            val result = executor.execute(call)
            appendAssistant(result.text, result.ui)
        } catch (e: Exception) {
            L.e("Agent: tool ${call.name} fail", e)
            appendAssistant("操作失败：${e.message ?: "未知错误"}")
        } finally {
            _state.value = _state.value.copy(typing = false)
        }
    }

    private fun appendAssistant(text: String, ui: UiBlock? = null) {
        _state.value = _state.value.copy(messages = _state.value.messages + AgentMessage("assistant", text, ui))
    }

    companion object {
        private const val MAX_ROUNDS = 4
        private const val WELCOME = "你好，我是蚂蚁影视助手 🤖\n可以直接搜索、分类浏览、点播影片。试试说“搜索 你好 世界”或“动漫”。"
    }
}

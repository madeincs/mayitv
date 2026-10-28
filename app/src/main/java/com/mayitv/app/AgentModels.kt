package com.mayitv.app

/**
 * Agent 数据模型。
 *
 * 设计原则：UI 是 Agent 状态的“投影”，Agent 是唯一状态源（单向数据流）。
 * LLM 永远不会直接生产任意 UI，只能从白名单 [UiBlock] 中让引擎投影结果。
 */

/** 展示给用户的一条消息 */
data class AgentMessage(
    val role: String,          // "user" | "assistant"
    val text: String,
    val ui: UiBlock? = null
)

/** Agent 整体状态 */
data class AgentUiState(
    val messages: List<AgentMessage> = emptyList(),
    val nav: UiBlock.Nav? = null,
    val typing: Boolean = false,
    val error: String? = null
)

/** 白名单 UI 块：只有这些类型允许渲染，杜绝任意代码注入 */
sealed class UiBlock {
    /** 动态生成的应用导航（主页顶部大卡片入口） */
    data class Nav(val title: String, val items: List<NavItem>) : UiBlock()

    /** 影片卡片网格（搜索/分类/首页结果） */
    data class Cards(val title: String, val items: List<Vod>) : UiBlock()

    /** 快捷操作芯片（点击回调整体工具） */
    data class Actions(val title: String, val items: List<ActionChip>) : UiBlock()
}

/** 导航卡片项：点击 -> 触发白名单工具 或 打开搜索 */
data class NavItem(
    val icon: String,
    val label: String,
    val subtitle: String,
    val tool: String,                       // 特殊值 "__search" 表示打开搜索输入
    val args: Map<String, String> = emptyMap()
)

/** 快捷操作芯片 */
data class ActionChip(
    val label: String,
    val tool: String,
    val args: Map<String, String> = emptyMap()
)

/** 工具元信息（供 LLM 感知可用工具，白名单） */
data class ToolSpec(
    val name: String,
    val description: String,
    val params: Map<String, String> = emptyMap()
)

/** LLM 发出的工具调用 */
data class LlmToolCall(
    val id: String,
    val name: String,
    val args: Map<String, String> = emptyMap()
)

/** 传给 LLM 的对话消息（OpenAI 兼容形状） */
data class LlmMessage(
    val role: String,                        // user | assistant | tool
    val content: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null
)

/** LLM 回复：纯文本 或 一组工具调用 */
data class LlmReply(
    val text: String = "",
    val toolCalls: List<LlmToolCall> = emptyList()
)

/** 工具执行结果：text 回喂 LLM，ui 投影给用户 */
data class ToolResult(
    val ok: Boolean,
    val text: String,
    val ui: UiBlock? = null
)

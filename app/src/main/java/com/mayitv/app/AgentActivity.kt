package com.mayitv.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mayitv.app.databinding.ActivityAgentBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Agent 主页（移动端）：
 *
 * - 默认显示“动态导航”主页（agent 产出的应用入口卡片）。
 * - 底部单按钮 tabbar 切换：AI 对话 <-> 首页。
 * - WebView 渲染两屏（nav/chat），交互经 [AgentBridge] 回流原生，复用现有播放链路。
 */
class AgentActivity : AppCompatActivity() {

    private lateinit var b: ActivityAgentBinding
    private lateinit var engine: AgentEngine
    private var pageReady = false
    private var viewChat = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAgentBinding.inflate(layoutInflater)
        setContentView(b.root)

        engine = AgentEngine(lifecycleScope, ToolExecutor(this))

        b.web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            userAgentString = SiteConfig.UA
        }
        b.web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageReady = true
                renderAll()
                applyView(chat = false)
            }

            // agent 内容不跳转外部页面
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
        }
        b.web.addJavascriptInterface(AgentBridge(), "agentBridge")
        b.web.loadDataWithBaseURL(SiteConfig.SITE, UiRenderer.shell(), "text/html", "utf-8", null)

        b.aiBtn.setOnClickListener { applyView(chat = !viewChat) }
        b.send.setOnClickListener { send() }
        b.input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) { send(); true } else false
        }

        lifecycleScope.launch {
            engine.state.collect { st ->
                if (pageReady) {
                    renderAll()
                    // 若对话状态在 nav 卡片点击后已切换，确保输入条可见
                    if (viewChat) b.inputBar.visibility = View.VISIBLE
                }
            }
        }
    }

    /** 渲染两屏（导航 + 对话），数据均来自不可变内核 [engine.state] */
    private fun renderAll() {
        val st = engine.state.value
        st.nav?.let {
            b.web.evaluateJavascript("agentRenderNav(${JSONObject.quote(UiRenderer.navHtml(it))})", null)
        }
        b.web.evaluateJavascript("agentRenderChat(${JSONObject.quote(UiRenderer.chatHtml(st))})", null)
    }

    /** 切换 导航主页 / 对话 视图 */
    private fun applyView(chat: Boolean) {
        viewChat = chat
        if (pageReady) b.web.evaluateJavascript("showView('${if (chat) "chat" else "nav"}')", null)
        b.inputBar.visibility = if (chat) View.VISIBLE else View.GONE
        b.aiBtn.text = if (chat) "⌂ 首页" else "🤖 AI 对话"
    }

    private fun send() {
        val t = b.input.text.toString().trim()
        if (t.isEmpty()) return
        b.input.text = null
        engine.submit(t)
    }

    /** JS bridge：HTML -> 原生 */
    inner class AgentBridge {
        @JavascriptInterface
        fun submitText(t: String?) {
            runOnUiThread { engine.submit(t ?: "") }
        }

        @JavascriptInterface
        fun toolCall(name: String?, argsJson: String?) {
            runOnUiThread { engine.runTool(name ?: "", parseArgs(argsJson)) }
        }

        /** 主页导航卡片：切换到对话并执行对应工具 */
        @JavascriptInterface
        fun navTool(name: String?, argsJson: String?) {
            runOnUiThread {
                applyView(chat = true)
                engine.runTool(name ?: "", parseArgs(argsJson))
            }
        }

        /** 搜索导航：切换到对话并聚焦输入框 */
        @JavascriptInterface
        fun openSearch() {
            runOnUiThread {
                applyView(chat = true)
                b.input.requestFocus()
            }
        }

        @JavascriptInterface
        fun playVod(id: String?) {
            runOnUiThread { engine.runTool("play", mapOf("id" to (id ?: ""))) }
        }
    }

    private fun parseArgs(s: String?): Map<String, String> = try {
        if (s.isNullOrBlank()) emptyMap() else {
            val o = JSONObject(s)
            val m = mutableMapOf<String, String>()
            val it = o.keys()
            while (it.hasNext()) { val k = it.next(); m[k] = o.optString(k) }
            m
        }
    } catch (e: Exception) {
        emptyMap()
    }

    override fun onBackPressed() {
        // 对话中按返回回到导航主页，而非直接退出
        if (viewChat) applyView(chat = false) else super.onBackPressed()
    }
}

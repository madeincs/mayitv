package com.mayitv.app

import org.json.JSONObject

/**
 * UiRenderer：Agent 状态 -> HTML（移动端，触摸优先）。
 *
 * - 所有动态文本经 [esc] 转义，杜绝注入；组件只来自白名单 [UiBlock]。
 * - 交互值一律放在 data-* 属性，由 shell JS 事件委托读取，绝不内联进 JS 代码。
 * - shell() 静态外壳含两屏：#nav（主页动态导航）与 #chat（对话），showView() 切换；
 *   agentRenderNav/agentRenderChat 仅更新对应容器，保留滚动与焦点。
 */
object UiRenderer {

    /** 静态外壳：只加载一次 */
    fun shell(): String = """
        <!DOCTYPE html>
        <html><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
        <style>
          * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
          html, body { height: 100%; }
          body {
            background: #0d0f1a;
            color: #e8eaf2;
            font-family: -apple-system, "PingFang SC", "Microsoft YaHei", Roboto, sans-serif;
            font-size: 16px;
            padding: 18px 16px 24px;
            -webkit-font-smoothing: antialiased;
          }
          /* ---- 导航主页 ---- */
          .navtitle { color: #9aa3c0; font-size: 15px; margin: 6px 0 18px; letter-spacing: .5px; }
          .navgrid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 14px; }
          .navcard {
            min-height: 128px; border-radius: 18px; padding: 16px;
            background: linear-gradient(150deg, #1c2140, #141830);
            border: 2px solid transparent; outline: none; cursor: pointer;
            display: flex; flex-direction: column; justify-content: space-between;
            transition: transform .12s ease, border-color .12s ease;
          }
          .navcard:active { transform: scale(.97); }
          .navcard:focus { border-color: #3d5afe; }
          .navicon { font-size: 32px; line-height: 1; }
          .navlabel { color: #eef1fb; font-size: 17px; font-weight: 600; margin-top: 14px; }
          .navsub { color: #8b93b3; font-size: 12px; margin-top: 3px; }
          /* ---- 对话 ---- */
          .msg { max-width: 88%; margin-bottom: 16px; line-height: 1.55; }
          .msg.user { margin-left: auto; text-align: right; }
          .bubble {
            display: inline-block; padding: 11px 15px; border-radius: 18px;
            white-space: pre-wrap; word-break: break-word;
          }
          .user .bubble { background: #3d5afe; color: #fff; border-bottom-right-radius: 6px; }
          .assistant .bubble { background: #1b2036; border-bottom-left-radius: 6px; }
          .typing .bubble { color: #9aa3c0; }
          .uiblock { margin-top: 10px; }
          .uiblock-title { color: #9aa3c0; font-size: 13px; margin: 4px 0 10px; }
          .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
          .card {
            background: #161a2c; border-radius: 12px; padding: 6px;
            outline: none; cursor: pointer; border: 2px solid transparent;
            transition: transform .12s ease, border-color .12s ease;
          }
          .card:active { transform: scale(.96); }
          .card:focus { border-color: #3d5afe; }
          .card img { width: 100%; aspect-ratio: 3/4; object-fit: cover; border-radius: 8px; background: #0d0f1a; display: block; }
          .card-t { margin-top: 5px; font-size: 12px; color: #e8eaf2; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
          .card-r { font-size: 11px; color: #ffb74d; margin-top: 2px; }
          .chips { display: flex; flex-wrap: wrap; gap: 10px; }
          .chip {
            background: #222842; color: #cfd6f2; border: 2px solid transparent;
            border-radius: 999px; padding: 10px 16px; font-size: 14px; outline: none; cursor: pointer;
          }
          .chip:active { background: #2a3156; }
          .chip:focus { border-color: #3d5afe; }
        </style>
        </head><body>
        <div id="nav"></div>
        <div id="chat" style="display:none"></div>
        <script>
          function showView(name){
            var nav = document.getElementById('nav');
            var chat = document.getElementById('chat');
            nav.style.display = (name === 'nav') ? 'block' : 'none';
            chat.style.display = (name === 'chat') ? 'block' : 'none';
          }
          function focusFirst(sel){
            var f = document.querySelector(sel + ' [tabindex]');
            if (f) f.focus();
          }
          window.agentRenderNav = function(html){
            document.getElementById('nav').innerHTML = html;
            focusFirst('#nav');
          };
          window.agentRenderChat = function(html){
            document.getElementById('chat').innerHTML = html;
            window.scrollTo(0, document.body.scrollHeight);
            focusFirst('#chat');
          };
          // 事件委托：交互值全部从 data-* 读取，避免内联注入
          document.body.addEventListener('click', function(e){
            var el = e.target;
            while (el && el !== document.body) {
              if (el.hasAttribute && el.hasAttribute('data-navsearch')) {
                window.agentBridge.openSearch(); return;
              }
              if (el.hasAttribute && el.hasAttribute('data-play')) {
                window.agentBridge.playVod(el.getAttribute('data-play')); return;
              }
              if (el.hasAttribute && el.hasAttribute('data-tool')) {
                var name = el.getAttribute('data-tool');
                var args = el.getAttribute('data-args') || '{}';
                if (el.hasAttribute('data-fromnav')) window.agentBridge.navTool(name, args);
                else window.agentBridge.toolCall(name, args);
                return;
              }
              el = el.parentNode;
            }
          });
        </script>
        </body></html>
    """.trimIndent()

    /** 主页动态导航片段 */
    fun navHtml(nav: UiBlock.Nav): String {
        val sb = StringBuilder()
        sb.append("""<div class="navtitle">${esc(nav.title)}</div><div class="navgrid">""")
        for (n in nav.items) {
            sb.append(
                if (n.tool == "__search") {
                    """<div class="navcard" tabindex="0" data-navsearch="1">
                         <div class="navicon">${esc(n.icon)}</div>
                         <div><div class="navlabel">${esc(n.label)}</div><div class="navsub">${esc(n.subtitle)}</div></div>
                       </div>"""
                } else {
                    """<div class="navcard" tabindex="0" data-fromnav="1" data-tool="${escAttr(n.tool)}" data-args="${escAttr(JSONObject(n.args).toString())}">
                         <div class="navicon">${esc(n.icon)}</div>
                         <div><div class="navlabel">${esc(n.label)}</div><div class="navsub">${esc(n.subtitle)}</div></div>
                       </div>"""
                }
            )
        }
        sb.append("</div>")
        return sb.toString()
    }

    /** 对话片段（每次状态更新重算） */
    fun chatHtml(state: AgentUiState): String {
        val sb = StringBuilder()
        for (m in state.messages) sb.append(message(m))
        if (state.typing) {
            sb.append("""<div class="msg assistant typing"><span class="bubble">正在思考…</span></div>""")
        }
        if (state.error != null) {
            sb.append("""<div class="msg assistant"><span class="bubble" style="color:#ff8a80">⚠ ${esc(state.error)}</span></div>""")
        }
        return sb.toString()
    }

    private fun message(m: AgentMessage): String {
        val cls = if (m.role == "user") "msg user" else "msg assistant"
        val ui = m.ui?.let { uiBlock(it) } ?: ""
        return """<div class="$cls"><span class="bubble">${esc(m.text)}</span>$ui</div>"""
    }

    private fun uiBlock(b: UiBlock): String = when (b) {
        is UiBlock.Nav -> navHtml(b)
        is UiBlock.Cards -> cards(b)
        is UiBlock.Actions -> actions(b)
    }

    private fun cards(b: UiBlock.Cards): String {
        val sb = StringBuilder()
        sb.append("""<div class="uiblock"><div class="uiblock-title">${esc(b.title)}</div><div class="grid">""")
        for (v in b.items) {
            sb.append(
                """<div class="card" tabindex="0" data-play="${escAttr(v.id)}">
                     <img src="${escAttr(v.cover)}" onerror="this.style.display='none'"/>
                     <div class="card-t">${esc(v.title)}</div>
                     <div class="card-r">${esc(v.remark)}</div>
                   </div>"""
            )
        }
        sb.append("</div></div>")
        return sb.toString()
    }

    private fun actions(b: UiBlock.Actions): String {
        val sb = StringBuilder()
        sb.append("""<div class="uiblock"><div class="uiblock-title">${esc(b.title)}</div><div class="chips">""")
        for (c in b.items) {
            val args = JSONObject(c.args).toString()
            sb.append(
                """<button class="chip" tabindex="0" data-tool="${escAttr(c.tool)}" data-args="${escAttr(args)}">${esc(c.label)}</button>"""
            )
        }
        sb.append("</div></div>")
        return sb.toString()
    }

    /** HTML 文本转义 */
    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    /** 属性值转义（HTML 属性上下文） */
    private fun escAttr(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

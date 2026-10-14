package com.mayitv.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * 浏览界面：直接加载站点（首页/分类/搜索/详情），站点自身 UI 完整可用。
 * 拦截 /vodplay/ 链接 -> 交给 PlayerActivity 用 ExoPlayer 原生播放。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = SiteConfig.UA
            // 放大文字，便于电视远距离观看
            textZoom = 120
        }

        web.webViewClient = object : WebViewClient() {
            private fun handle(url: String): Boolean {
                if (url.contains("/vodplay/") && url.contains(".html")) {
                    startActivity(
                        Intent(this@MainActivity, PlayerActivity::class.java)
                            .putExtra("url", url)
                    )
                    return true
                }
                // 同站链接放行，外部链接拦截
                return !url.contains("91mayitv.com") && !url.startsWith("about:")
            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String) = handle(url)

            override fun shouldOverrideUrlLoading(
                view: WebView, req: WebResourceRequest
            ) = handle(req.url.toString())

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(FOCUS_JS, null)
            }
        }
        web.webChromeClient = WebChromeClient()
        web.loadUrl(SiteConfig.SITE + "/")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    companion object {
        // 注入：聚焦高亮 + 自动滚动到可视区 + 移除部分广告
        private const val FOCUS_JS = """
        (function(){
          var s=document.createElement('style');
          s.innerHTML='a:focus,[tabindex]:focus,*:focus{outline:3px solid #FFB300 !important;outline-offset:2px;}';
          document.head.appendChild(s);
          document.addEventListener('focusin',function(e){
            try{e.target.scrollIntoView({block:'center',inline:'center'});}catch(_){}
          },true);
        })();
        """
    }
}

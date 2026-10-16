package com.mayitv.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mayitv.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/** 原生首页：品牌栏 + 胶囊分类标签 + 影片网格 + 滚动翻页 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var adapter: VodAdapter
    private val tabButtons = mutableListOf<Button>()

    // 名称 -> 类型(0=首页, 1=电影, 2=连续剧, 3=综艺, 4=动漫)
    private val tabs = listOf("首页" to 0, "电影" to 1, "连续剧" to 2, "综艺" to 3, "动漫" to 4)
    private var currentType = 0
    private var page = 1
    private var loading = false
    private var hasMore = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        adapter = VodAdapter { v ->
            startActivity(
                Intent(this, DetailActivity::class.java)
                    .putExtra("id", v.id)
                    .putExtra("title", v.title)
            )
        }
        val span = (resources.displayMetrics.widthPixels / 300).coerceIn(4, 8)
        b.grid.layoutManager = GridLayoutManager(this, span)
        b.grid.adapter = adapter
        b.grid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val lm = rv.layoutManager as GridLayoutManager
                if (!loading && hasMore && lm.itemCount > 0 &&
                    lm.findLastVisibleItemPosition() >= lm.itemCount - span * 2
                ) loadMore()
            }
        })

        buildTabs()
        loadHome()
    }

    private fun makePill(text: String): Button {
        val btn = Button(this)
        btn.text = text
        btn.isFocusable = true
        btn.isAllCaps = false
        btn.background = ContextCompat.getDrawable(this, R.drawable.tab_pill)
        btn.backgroundTintList = null
        btn.paddingStart = dp(18)
        btn.paddingEnd = dp(18)
        btn.paddingTop = dp(9)
        btn.paddingBottom = dp(9)
        btn.setTextSize(14f)
        btn.setTextColor(ContextCompat.getColorStateList(this, R.color.tab_text))
        return btn
    }

    private fun buildTabs() {
        b.tabs.removeAllViews()
        tabButtons.clear()
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(10) }

        for ((name, type) in tabs) {
            val btn = makePill(name)
            btn.setOnClickListener { selectType(type) }
            tabButtons.add(btn)
            b.tabs.addView(btn, lp)
        }
        b.tabs.addView(makePill("搜索").apply {
            isFocusable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, SearchActivity::class.java)) }
        }, lp)
        b.tabs.addView(makePill("检查更新").apply {
            isFocusable = true
            setOnClickListener { checkUpdate() }
        }, lp)
        refreshTabStyles()
        b.tabs.getChildAt(0).requestFocus()
    }

    private fun refreshTabStyles() {
        tabButtons.forEachIndexed { i, btn -> btn.isActivated = tabs[i].second == currentType }
    }

    private fun selectType(type: Int) {
        if (type == currentType) return
        currentType = type
        page = 1
        hasMore = type != 0
        adapter.clear()
        refreshTabStyles()
        if (type == 0) loadHome() else loadCategory(type, 1)
    }

    private fun loadHome() {
        loading = true
        L.d("Main: loadHome")
        lifecycleScope.launch {
            adapter.set(Repo.home())
            loading = false
        }
    }

    private fun loadCategory(type: Int, p: Int) {
        loading = true
        L.d("Main: loadCategory($type,$p)")
        lifecycleScope.launch {
            val list = Repo.category(type, p)
            if (p == 1) adapter.set(list) else adapter.add(list)
            if (list.isEmpty()) hasMore = false
            loading = false
        }
    }

    private fun loadMore() {
        if (currentType == 0) return
        page++
        loadCategory(currentType, page)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    /** 检查更新：私有仓库/查询失败时回退到浏览器打开下载页 */
    private fun checkUpdate() {
        toast("检查更新中…")
        L.d("Main: checkUpdate current=(${BuildConfig.VERSION_CODE})")
        lifecycleScope.launch {
            val rel = UpdateChecker.latest()
            if (rel == null) {
                L.w("Main: 更新查询失败，回退浏览器")
                startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(UpdateChecker.webUrl))
                )
                return@launch
            }
            L.d("Main: 最新版本 v${rel.versionCode} url=${rel.apkUrl}")
            if (rel.versionCode <= BuildConfig.VERSION_CODE) {
                toast("已是最新版本")
                return@launch
            }
            toast("发现新版本 v${rel.versionCode}，开始下载…")
            val file = UpdateChecker.download(this@MainActivity, rel.apkUrl)
            if (file == null) {
                L.e("Main: APK 下载失败")
                toast("下载失败，请重试或手动更新")
                return@launch
            }
            L.d("Main: 下载完成 ${file.length()} bytes")
            installApk(file)
        }
    }

    private fun installApk(file: java.io.File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}

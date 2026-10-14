package com.mayitv.app

import android.content.Intent
import android.graphics.Color
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

/** 原生首页：顶部分类标签 + 影片网格 + 滚动翻页 */
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
    private val accent by lazy { ContextCompat.getColor(this, R.color.accent) }

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

    private fun buildTabs() {
        b.tabs.removeAllViews()
        tabButtons.clear()
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = 12 }

        for ((name, type) in tabs) {
            val btn = Button(this).apply {
                text = name
                isFocusable = true
                isAllCaps = false
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.tab_bg)
                setTextColor(Color.WHITE)
                setOnClickListener { selectType(type) }
            }
            tabButtons.add(btn)
            b.tabs.addView(btn, lp)
        }
        val search = Button(this).apply {
            text = "搜索"
            isFocusable = true
            isAllCaps = false
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.tab_bg)
            setTextColor(Color.WHITE)
            setOnClickListener { startActivity(Intent(this@MainActivity, SearchActivity::class.java)) }
        }
        b.tabs.addView(search, lp)
        tabButtons.first().setTextColor(accent)
        b.tabs.getChildAt(0).requestFocus()
    }

    private fun selectType(type: Int) {
        if (type == currentType) return
        currentType = type
        page = 1
        hasMore = type != 0
        adapter.clear()
        tabButtons.forEachIndexed { i, btn ->
            btn.setTextColor(if (tabs[i].second == type) accent else Color.WHITE)
        }
        if (type == 0) loadHome() else loadCategory(type, 1)
    }

    private fun loadHome() {
        loading = true
        lifecycleScope.launch {
            adapter.set(Repo.home())
            loading = false
        }
    }

    private fun loadCategory(type: Int, p: Int) {
        loading = true
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
}

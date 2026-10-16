package com.mayitv.app

import android.content.Intent
import android.graphics.Outline
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.mayitv.app.databinding.ActivityDetailBinding
import kotlinx.coroutines.launch

/** 详情页：封面/信息/简介 + 多播放源切换 + 选集 */
class DetailActivity : AppCompatActivity() {

    private lateinit var b: ActivityDetailBinding
    private lateinit var epAdapter: EpisodeAdapter
    private val sourceButtons = mutableListOf<Button>()
    private var detail: VodDetail? = null
    private var currentSource = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(b.root)

        val id = intent.getStringExtra("id") ?: run { finish(); return }
        b.title.text = intent.getStringExtra("title") ?: ""

        // 封面四角圆角，与卡片圆角对齐
        b.cover.viewOutlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, 14f)
        }
        b.cover.clipToOutline = true

        epAdapter = EpisodeAdapter { ep -> play(ep) }
        b.episodes.layoutManager = GridLayoutManager(this, 6)
        b.episodes.adapter = epAdapter
        b.episodes.isNestedScrollingEnabled = false

        lifecycleScope.launch {
            val d = Repo.detail(id)
            if (d == null) {
                L.e("Detail: 加载失败 id=$id")
                b.title.text = "加载失败，请返回重试"
                return@launch
            }
            L.d("Detail: ${d.title} sources=${d.sources.size}")
            detail = d
            b.title.text = d.title
            b.info.text = d.info
            b.desc.text = d.desc
            Glide.with(this@DetailActivity).load(d.cover)
                .placeholder(R.drawable.placeholder).error(R.drawable.placeholder).into(b.cover)
            buildSources()
        }
    }

    private fun buildSources() {
        val d = detail ?: return
        b.sources.removeAllViews()
        sourceButtons.clear()
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(10) }

        d.sources.forEachIndexed { idx, src ->
            val btn = Button(this).apply {
                text = src.name
                isFocusable = true
                isAllCaps = false
                background = ContextCompat.getDrawable(this@DetailActivity, R.drawable.tab_pill)
                backgroundTintList = null
                paddingStart = dp(18)
                paddingEnd = dp(18)
                paddingTop = dp(9)
                paddingBottom = dp(9)
                textSize = 14f
                setTextColor(ContextCompat.getColorStateList(this@DetailActivity, R.color.tab_text))
                setOnClickListener {
                    currentSource = idx
                    showEpisodes()
                    refreshSourceStyles()
                }
            }
            sourceButtons.add(btn)
            b.sources.addView(btn, lp)
        }
        showEpisodes()
        refreshSourceStyles()
    }

    private fun refreshSourceStyles() {
        sourceButtons.forEachIndexed { i, btn -> btn.isActivated = (i == currentSource) }
    }

    private fun showEpisodes() {
        val src = detail?.sources?.getOrNull(currentSource) ?: return
        epAdapter.set(src.episodes)
    }

    private fun play(ep: Episode) {
        val d = detail ?: return
        L.d("Detail: play ${ep.name} url=${ep.playUrl}")
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("url", ep.playUrl)
                .putExtra("title", "${d.title} · ${ep.name}")
        )
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

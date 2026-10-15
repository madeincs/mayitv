package com.mayitv.app

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mayitv.app.databinding.ActivitySearchBinding
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var b: ActivitySearchBinding
    private lateinit var adapter: VodAdapter
    private var kw = ""
    private var page = 1
    private var loading = false
    private var hasMore = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySearchBinding.inflate(layoutInflater)
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
                if (!loading && hasMore && kw.isNotEmpty() && lm.itemCount > 0 &&
                    lm.findLastVisibleItemPosition() >= lm.itemCount - span * 2
                ) {
                    page++
                    doSearch(page)
                }
            }
        })

        b.edit.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = v.text.toString().trim()
                if (q.isNotEmpty()) {
                    kw = q
                    page = 1
                    hasMore = true
                    adapter.clear()
                    doSearch(1)
                }
                true
            } else false
        }
        b.edit.requestFocus()
    }

    private fun doSearch(p: Int) {
        loading = true
        L.d("Search: doSearch('$kw',$p)")
        lifecycleScope.launch {
            val list = Repo.search(kw, p)
            if (p == 1) adapter.set(list) else adapter.add(list)
            if (list.isEmpty()) hasMore = false
            loading = false
        }
    }
}

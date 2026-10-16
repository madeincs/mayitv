package com.mayitv.app

import android.graphics.Outline
import android.graphics.Path
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class VodAdapter(private val onClick: (Vod) -> Unit) : RecyclerView.Adapter<VodAdapter.VH>() {

    private val items = mutableListOf<Vod>()

    fun set(list: List<Vod>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    fun add(list: List<Vod>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun clear() { items.clear(); notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_vod, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cover: ImageView = itemView.findViewById(R.id.cover)
        private val title: TextView = itemView.findViewById(R.id.title)
        private val remark: TextView = itemView.findViewById(R.id.remark)
        private val ring: View = itemView.findViewById(R.id.focusRing)

        init {
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
            // 封面只圆上两角，与卡片圆角对齐
            cover.viewOutlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    val r = 14f
                    val p = Path()
                    p.addRoundRect(
                        RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()),
                        floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f),
                        Path.Direction.CW
                    )
                    outline.setConvexPath(p)
                }
            }
            cover.clipToOutline = true
            itemView.setOnFocusChangeListener { v, hasFocus ->
                ring.visibility = if (hasFocus) View.VISIBLE else View.GONE
                val s = if (hasFocus) 1.06f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
                v.z = if (hasFocus) 10f else 0f
            }
        }

        fun bind(v: Vod) {
            title.text = v.title
            remark.visibility = if (v.remark.isBlank()) View.GONE else View.VISIBLE
            remark.text = v.remark
            Glide.with(itemView).load(v.cover).placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder).into(cover)
            itemView.setOnClickListener { onClick(v) }
        }
    }
}

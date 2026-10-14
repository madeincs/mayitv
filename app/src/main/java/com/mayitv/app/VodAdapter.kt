package com.mayitv.app

import android.view.LayoutInflater
import android.view.View
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

        init {
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
            itemView.setOnFocusChangeListener { v, hasFocus ->
                val s = if (hasFocus) 1.08f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
                v.z = if (hasFocus) 10f else 0f
            }
        }

        fun bind(v: Vod) {
            title.text = v.title
            remark.text = v.remark
            Glide.with(itemView).load(v.cover).placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder).into(cover)
            itemView.setOnClickListener { onClick(v) }
        }
    }
}

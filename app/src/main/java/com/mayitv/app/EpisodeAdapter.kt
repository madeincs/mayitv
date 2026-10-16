package com.mayitv.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EpisodeAdapter(private val onClick: (Episode) -> Unit) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

    private val items = mutableListOf<Episode>()

    fun set(list: List<Episode>) {
        items.clear(); items.addAll(list); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tv: TextView = itemView.findViewById(R.id.name)

        init {
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
            itemView.setOnFocusChangeListener { v, hasFocus ->
                val s = if (hasFocus) 1.06f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(120).start()
                v.z = if (hasFocus) 6f else 0f
            }
        }

        fun bind(e: Episode) {
            tv.text = e.name
            itemView.setOnClickListener { onClick(e) }
        }
    }
}

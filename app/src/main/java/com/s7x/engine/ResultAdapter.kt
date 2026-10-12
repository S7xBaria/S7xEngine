package com.s7x.engine

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ResultAdapter(private var items: MutableList<ResultItem>,
                    private val onToggle: (Int) -> Unit,
                    private val onLongClick: (Int) -> Unit) :
    RecyclerView.Adapter<ResultAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cb: CheckBox = v.findViewById(R.id.cbItem)
        val tvAddr: TextView = v.findViewById(R.id.tvAddr)
        val tvVal: TextView = v.findViewById(R.id.tvVal)
        val tvFrozen: TextView = v.findViewById(R.id.tvFrozen)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int): VH =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_result, p, false))
    override fun onBindViewHolder(h: VH, pos: Int) {
        val it = items[pos]
        h.tvAddr.text = "0x" + it.address.toString(16).uppercase(Locale.ROOT)
        h.tvVal.text = it.value
        h.tvFrozen.visibility = if (it.frozen) View.VISIBLE else View.GONE
        h.cb.setOnCheckedChangeListener(null)
        h.cb.isChecked = it.selected
        h.cb.setOnCheckedChangeListener { _, _ -> onToggle(pos) }
        h.itemView.setOnLongClickListener { onLongClick(pos); true }
    }
    override fun getItemCount() = items.size
    fun update(n: MutableList<ResultItem>) { items = n; notifyDataSetChanged() }
}

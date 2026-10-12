package com.s7x.engine

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProcessAdapter(private var items: List<MemoryEngine.ProcessInfo>,
                     private val onClick: (MemoryEngine.ProcessInfo) -> Unit) :
    RecyclerView.Adapter<ProcessAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvProcName)
        val tvPid: TextView = v.findViewById(R.id.tvProcPid)
        val tvArch: TextView = v.findViewById(R.id.tvProcArch)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int): VH =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_process, p, false))
    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.tvName.text = item.name
        h.tvPid.text = "[" + item.pid + "]"
        h.tvArch.text = "[" + item.arch + "]"
        h.itemView.setOnClickListener { onClick(item) }
    }
    override fun getItemCount() = items.size
    fun update(n: List<MemoryEngine.ProcessInfo>) { items = n; notifyDataSetChanged() }
}

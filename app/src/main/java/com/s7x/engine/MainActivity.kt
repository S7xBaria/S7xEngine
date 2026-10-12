package com.s7x.engine

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.s7x.engine.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private lateinit var pa: ProcessAdapter
    private lateinit var ra: ResultAdapter
    private var spid = -1
    private var sname = ""
    private var sarch = ""
    private var res: MutableList<ResultItem> = mutableListOf()
    private var all: List<MemoryEngine.ProcessInfo> = emptyList()
    private var ct = MemoryEngine.TYPE_DWORD
    private var fj: Job? = null

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        if (!RootHelper.isRooted()) Toast.makeText(this, "Root غير متوفر", Toast.LENGTH_LONG).show()
        else Toast.makeText(this, "S7xEngine v5.0 (Root)", Toast.LENGTH_SHORT).show()
        pa = ProcessAdapter(emptyList()) { sp(it) }
        b.rvProcesses.layoutManager = LinearLayoutManager(this)
        b.rvProcesses.adapter = pa
        ra = ResultAdapter(res, { p -> res[p].selected = !res[p].selected }, { p -> ed(p) })
        b.rvResults.layoutManager = LinearLayoutManager(this)
        b.rvResults.adapter = ra
        ui(); load()
    }

    private fun ui() {
        b.btnRefresh.setOnClickListener { load() }
        b.btnSearch.setOnClickListener {
            val q = b.etSearch.text.toString().trim()
            pa.update(if (q.isEmpty()) all else all.filter { it.name.contains(q, true) })
        }
        val ts = arrayOf("Byte", "Word", "DWord", "QWord", "Float", "Double")
        b.spType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ts)
        b.spType.setSelection(2)
        b.spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, po: Int, id: Long) { ct = po }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        b.btnScanEqual.setOnClickListener { sc(MemoryEngine.MODE_EQUAL) }
        b.btnScanGreater.setOnClickListener { sc(MemoryEngine.MODE_GREATER) }
        b.btnScanLess.setOnClickListener { sc(MemoryEngine.MODE_LESS) }
        b.btnScanRange.setOnClickListener { sc(MemoryEngine.MODE_RANGE) }
        b.btnFilter.setOnClickListener { fm() }
        b.btnWriteAll.setOnClickListener { wa() }
        b.btnWriteSelected.setOnClickListener { ws() }
        b.btnFreeze.setOnClickListener { tf() }
        b.btnClear.setOnClickListener { res.clear(); ra.update(res); b.tvStatus.text = "Cleared" }
        b.btnSaved.setOnClickListener { sm() }
        b.btnRegions.setOnClickListener { sr() }
        b.btnSelectAll.setOnClickListener {
            val a = res.any { !it.selected }
            res.forEach { it.selected = a }
            ra.notifyDataSetChanged()
        }
    }

    private fun load() {
        lifecycleScope.launch {
            b.tvStatus.text = "Loading..."
            val l = withContext(Dispatchers.IO) { MemoryEngine.listProcesses() }
            all = l; pa.update(l); b.tvStatus.text = l.size.toString() + " procs"
        }
    }

    private fun sp(p: MemoryEngine.ProcessInfo) {
        spid = p.pid; sname = p.name; sarch = p.arch
        b.tvSelected.text = p.name + " [" + p.pid + "] [" + p.arch + "]"
        b.tvStatus.text = "Ready"
        res.clear(); ra.update(res)
    }

    private fun sc(m: Int) {
        if (spid <= 0) { t("اختر عملية"); return }
        val v = b.etValue.text.toString().trim()
        if (v.isEmpty()) { t("أدخل قيمة"); return }
        lifecycleScope.launch {
            b.tvStatus.text = "Scan..."
            val r = withContext(Dispatchers.IO) {
                MemoryEngine.scan(spid, v, ct, m) { m2 ->
                    lifecycleScope.launch(Dispatchers.Main) { b.tvStatus.text = m2 }
                }
            }
            res = r.map { ResultItem(it, MemoryEngine.readValueString(spid, it, ct)) }.toMutableList()
            if (res.size > 500) res = res.take(500).toMutableList()
            ra.update(res); b.tvStatus.text = "Res: " + res.size
        }
    }

    private fun fm() {
        if (res.isEmpty()) { t("ابحث أولاً"); return }
        val o = arrayOf("Equal", "Greater", "Less", "Changed", "Unchanged", "Increased", "Decreased")
        AlertDialog.Builder(this).setTitle("تصفية (" + res.size + ")")
            .setItems(o) { _, w -> afl(w) }.setNegativeButton("إلغاء", null).show()
    }

    private fun afl(m: Int) {
        val v = b.etValue.text.toString().trim().ifEmpty { "0" }
        lifecycleScope.launch {
            b.tvStatus.text = "Filter..."
            val ad = res.map { it.address }
            val r = withContext(Dispatchers.IO) { MemoryEngine.filter(spid, ad, v, ct, m) }
            res = r.map { ResultItem(it, MemoryEngine.readValueString(spid, it, ct)) }.toMutableList()
            if (res.size > 500) res = res.take(500).toMutableList()
            ra.update(res); b.tvStatus.text = "After: " + res.size
        }
    }

    private fun wa() {
        if (res.isEmpty()) { t("ابحث"); return }
        val nv = b.etNewValue.text.toString().trim()
        if (nv.isEmpty()) { t("أدخل القيمة"); return }
        lifecycleScope.launch {
            var c = 0
            withContext(Dispatchers.IO) {
                for (r in res) if (MemoryEngine.writeValue(spid, r.address, nv, ct)) { r.value = nv; c++ }
            }
            ra.notifyDataSetChanged(); t("تعديل " + c)
        }
    }

    private fun ws() {
        val s = res.filter { it.selected }
        if (s.isEmpty()) { t("حدد عناوين"); return }
        val nv = b.etNewValue.text.toString().trim()
        if (nv.isEmpty()) { t("أدخل القيمة"); return }
        lifecycleScope.launch {
            var c = 0
            withContext(Dispatchers.IO) {
                for (r in s) if (MemoryEngine.writeValue(spid, r.address, nv, ct)) { r.value = nv; c++ }
            }
            ra.notifyDataSetChanged(); t("تعديل " + c)
        }
    }

    private fun tf() {
        if (res.isEmpty()) { t("ابحث"); return }
        if (fj?.isActive == true) {
            fj?.cancel(); fj = null; b.btnFreeze.text = "تجميد"; t("إلغاء"); return
        }
        val f = res.filter { it.selected }
        if (f.isEmpty()) { t("حدد عناوين"); return }
        b.btnFreeze.text = "إيقاف"; t("تجميد " + f.size)
        fj = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                for (r in f) MemoryEngine.writeValue(spid, r.address, r.value, ct)
                delay(200)
            }
        }
    }

    private fun ed(p: Int) {
        val r = res[p]
        val i = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(r.value); setSelection(r.value.length)
        }
        AlertDialog.Builder(this).setTitle("تعديل " + r.address.toString(16).uppercase(Locale.ROOT))
            .setView(i).setPositiveButton("تعديل") { _, _ ->
                val nv = i.text.toString()
                lifecycleScope.launch(Dispatchers.IO) {
                    MemoryEngine.writeValue(spid, r.address, nv, ct); r.value = nv
                    withContext(Dispatchers.Main) { ra.notifyItemChanged(p) }
                }
            }.setNeutralButton("Hex") { _, _ -> sx(r.address) }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun sx(a: Long) {
        lifecycleScope.launch {
            val d = withContext(Dispatchers.IO) { MemoryEngine.hexDump(spid, a, 256) }
            AlertDialog.Builder(this@MainActivity).setTitle("Hex " + a.toString(16).uppercase(Locale.ROOT))
                .setMessage(d).setPositiveButton("OK", null).show()
        }
    }

    private fun sm() {
        val o = arrayOf("حفظ", "تحميل", "عرض")
        AlertDialog.Builder(this).setTitle("قائمة").setItems(o) { _, w ->
            when (w) { 0 -> sv(); 1 -> ld(); 2 -> shw() }
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun sv() {
        if (res.isEmpty()) { t("لا نتائج"); return }
        val i = EditText(this).apply { hint = "الاسم"; setText("list_" + System.currentTimeMillis() / 1000) }
        AlertDialog.Builder(this).setTitle("حفظ").setView(i).setPositiveButton("حفظ") { _, _ ->
            val n = i.text.toString().ifEmpty { "list" }
            val f = File(getExternalFilesDir(null), n + ".s7x")
            val sb = StringBuilder()
            sb.append("#TYPE:").append(ct).append("\n")
            for (r in res) sb.append(r.address).append(",").append(r.value).append("\n")
            f.writeText(sb.toString()); t("حفظ: " + f.name)
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun ld() {
        val d = getExternalFilesDir(null) ?: return
        val fs = d.listFiles { f -> f.name.endsWith(".s7x") } ?: return
        if (fs.isEmpty()) { t("لا قوائم"); return }
        AlertDialog.Builder(this).setTitle("تحميل").setItems(fs.map { it.name }.toTypedArray()) { _, w ->
            val c = fs[w].readText()
            val nr = mutableListOf<ResultItem>()
            var ty = MemoryEngine.TYPE_DWORD
            for (l in c.lines()) {
                if (l.startsWith("#TYPE:")) ty = l.substringAfter(":").toIntOrNull() ?: ty
                else if (l.startsWith("#")) continue
                else {
                    val p = l.split(",")
                    if (p.size >= 2) {
                        val a = p[0].toLongOrNull() ?: continue
                        nr.add(ResultItem(a, p[1]))
                    }
                }
            }
            ct = ty; b.spType.setSelection(ty); res = nr; ra.update(res); t("حمل: " + res.size)
        }.setNegativeButton("إلغاء", null).show()
    }

    private fun shw() {
        if (res.isEmpty()) { t("لا نتائج"); return }
        AlertDialog.Builder(this).setTitle("القائمة (" + res.size + ")")
            .setItems(res.map { "0x" + it.address.toString(16).uppercase(Locale.ROOT) + " = " + it.value }.toTypedArray(), null)
            .setPositiveButton("إغلاق", null).show()
    }

    private fun sr() {
        if (spid <= 0) { t("اختر عملية"); return }
        lifecycleScope.launch {
            val m = withContext(Dispatchers.IO) { MemoryEngine.getMaps(spid) }
            AlertDialog.Builder(this).setTitle("مناطق (" + m.size + ")")
                .setMessage(m.take(100).joinToString("\n") {
                    "0x" + it.start.toString(16) + "-0x" + it.end.toString(16) + " " + it.perms
                }).setPositiveButton("إغلاق", null).show()
        }
    }

    private fun t(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}

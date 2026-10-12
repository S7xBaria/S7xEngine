package com.s7x.engine
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.s7x.engine.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
class MainActivity : AppCompatActivity() {
private lateinit var b: ActivityMainBinding
private lateinit var pa: ProcessAdapter
private lateinit var ra: ResultAdapter
private var spid= -1
private var sname=""
private var sarch=""
private var res: MutableList<ResultItem> = mutableListOf()
private var all: List<MemoryEngine.ProcessInfo> = emptyList()
private var ct=MemoryEngine.TYPE_DWORD
private var fj: Job?=null
private var fa: List<Long> = emptyList()
private var fv: List<Long> = emptyList()
override fun onCreate(s: Bundle?) { super.onCreate(s); b=ActivityMainBinding.inflate(layoutInflater); setContentView(b.root)
val f=AnimationUtils.loadAnimation(this,android.R.anim.fade_in); b.tvTitle.startAnimation(f)
if(!RootHelper.isRooted()) Toast.makeText(this,"Root not available",Toast.LENGTH_LONG).show() else Toast.makeText(this,"S7xEngine v5.0",Toast.LENGTH_SHORT).show()
pa=ProcessAdapter(emptyList()) { sp(it) }; b.rvProcesses.layoutManager=LinearLayoutManager(this); b.rvProcesses.adapter=pa
ra=ResultAdapter(res, { p -> res[p].selected=!res[p].selected }, { p -> ed(p) }); b.rvResults.layoutManager=LinearLayoutManager(this); b.rvResults.adapter=ra
ui(); load() }
private fun ui() {
b.btnRefresh.setOnClickListener { load() }
b.btnSearch.setOnClickListener { val q=b.etSearch.text.toString().trim(); pa.update(if(q.isEmpty()) all else all.filter { it.name.contains(q,true) }) }
val ts=arrayOf("Byte","Word","DWord","QWord","Float","Double")
b.spType.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,ts); b.spType.setSelection(2)
b.spType.onItemSelectedListener=object: AdapterView.OnItemSelectedListener { override fun onItemSelected(p: AdapterView<*>?, v: View?, po: Int, id: Long){ ct=po }; override fun onNothingSelected(p: AdapterView<*>?){} }
b.btnScanEqual.setOnClickListener { sc(MemoryEngine.MODE_EQUAL) }
b.btnScanGreater.setOnClickListener { sc(MemoryEngine.MODE_GREATER) }
b.btnScanLess.setOnClickListener { sc(MemoryEngine.MODE_LESS) }
b.btnScanRange.setOnClickListener { sc(MemoryEngine.MODE_RANGE) }
b.btnFilter.setOnClickListener { fm() }
b.btnWriteAll.setOnClickListener { wa() }
b.btnWriteSelected.setOnClickListener { ws() }
b.btnFreeze.setOnClickListener { tf() }
b.btnClear.setOnClickListener { res.clear(); ra.update(res); b.tvStatus.text="Cleared" }
b.btnSaved.setOnClickListener { sm() }
b.btnRegions.setOnClickListener { sr() }
b.btnSelectAll.setOnClickListener { val a=res.any { !it.selected }; res.forEach { it.selected=a }; ra.notifyDataSetChanged() }
b.btnSpeedhack.setOnClickListener { sh() }
b.btnSearchMode.setOnClickListener { smm() }
b.btnFuzzy.setOnClickListener { fz() }
b.btnPointer.setOnClickListener { pt() }
}
private fun fz() { if(spid<=0){ t("Pick app"); return }
val o=arrayOf("Snapshot","Greater","Less","Changed","Unchanged","Increased","Decreased")
AlertDialog.Builder(this).setTitle("Fuzzy").setItems(o) { _, w -> if(w==0) snap() else af(w) }.setNegativeButton("Cancel",null).show() }
private fun snap() { lifecycleScope.launch { b.tvStatus.text="Snapshot..."
val r=withContext(Dispatchers.IO) { MemoryEngine.fuzzySnapshot(spid,ct) { m -> lifecycleScope.launch(Dispatchers.Main) { b.tvStatus.text=m } } }
fa=r.first; fv=r.second; b.tvStatus.text="Saved "+fa.size; t("Saved "+fa.size) } }
private fun af(m: Int) { if(fa.isEmpty()){ t("Snapshot first"); return }
val i=EditText(this).apply { hint="Delta"; inputType=InputType.TYPE_CLASS_NUMBER; setText("0") }
AlertDialog.Builder(this).setTitle("Filter").setView(i).setPositiveButton("Go") { _, _ ->
val d=i.text.toString().toIntOrNull()?:0
lifecycleScope.launch { b.tvStatus.text="Filtering..."
val r=withContext(Dispatchers.IO) { MemoryEngine.fuzzyFilter(spid,fa,fv,ct,m,d) }
fa=r.first; fv=r.second
res=r.first.map { ResultItem(it,MemoryEngine.readValueString(spid,it,ct)) }.toMutableList()
if(res.size>500) res=res.take(500).toMutableList()
ra.update(res); b.tvStatus.text="After: "+res.size } }.setNegativeButton("Cancel",null).show() }
private fun pt() { if(spid<=0){ t("Pick app"); return }
if(res.isEmpty()){ t("Search first"); return }
val tg=res.first().address
lifecycleScope.launch { b.tvStatus.text="Pointers..."
val r=withContext(Dispatchers.IO) { MemoryEngine.scanPointer(spid,tg,0x1000) { m -> lifecycleScope.launch(Dispatchers.Main) { b.tvStatus.text=m } } }
res=r.map { ResultItem(it,MemoryEngine.readValueString(spid,it,MemoryEngine.TYPE_QWORD)) }.toMutableList()
if(res.size>500) res=res.take(500).toMutableList()
ra.update(res); b.tvStatus.text="Ptr: "+res.size } }
private fun smm() { val o=arrayOf("Numeric","XOR","String UTF-8","String UTF-16","Group")
AlertDialog.Builder(this).setTitle("Mode").setItems(o) { _, w -> when(w){ 0->sc(MemoryEngine.MODE_EQUAL); 1->xor(); 2->str(false); 3->str(true); 4->grp() } }.setNegativeButton("Cancel",null).show() }
private fun xor() { if(spid<=0){ t("Pick app"); return }
val v=b.etValue.text.toString().trim().toIntOrNull()?:return
val i=EditText(this).apply { hint="XOR key"; inputType=InputType.TYPE_CLASS_NUMBER; setText("1") }
AlertDialog.Builder(this).setTitle("XOR").setView(i).setPositiveButton("Go") { _, _ ->
val k=i.text.toString().toIntOrNull()?:0
lifecycleScope.launch { b.tvStatus.text="XOR..."
val r=withContext(Dispatchers.IO) { MemoryEngine.scanXor(spid,v,k) { m -> lifecycleScope.launch(Dispatchers.Main) { b.tvStatus.text=m } } }
res=r.map { ResultItem(it,MemoryEngine.readValueString(spid,it,ct)) }.toMutableList()
if(res.size>500) res=res.take(500).toMutableList(); ra.update(res); b.tvStatus.text="Res: "+res.size } }.setNegativeButton("Cancel",null).show() }
private fun str(u: Boolean) { if(spid<=0){ t("Pick app"); return }
val v=b.etValue.text.toString().trim(); if(v.isEmpty()){ t("Enter text"); return }
lifecycleScope.launch { b.tvStatus.text="String..."
val r=withContext(Dispatchers.IO) { MemoryEngine.scanString(spid,v,u) { m -> lifecycleScope.launch(Dispatchers.Main) { b.tvStatus.text=m } } }
res=r.map { ResultItem(it,"STR") }.toMutableList()
if(res.size>500) res=res.take(500).toMutableList(); ra.update(res); b.tvStatus.text="Str: "+res.size } }
private fun grp() { if(spid<=0){ t("Pick app"); return }
val i=EditText(this).apply { hint="Values: 100,200,300"; inputType=InputType.TYPE_CLASS_TEXT }
val g=EditText(this).apply { hint="Gap"; inputType=InputType.TYPE_CLASS_NUMBER; setText("4") }
val ll=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; addView(i); addView(g) }
AlertDialog.Builder(this).setTitle("Group").setView(ll).setPositiveButton("Go") { _, _ ->
val ns=i.text.toString().split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
if(ns.size<2){ t("2+ values"); return@setPositiveButton }
val gp=g.text.toString().toIntOrNull()?:4
lifecycleScope.launch { b.tvStatus.text="Group..."
val r=withContext(Dispatchers.IO) { MemoryEngine.scanGroup(spid,ns,gp) { m -> lifecycleScope.launch(Dispatchers.Main) { b.tvStatus.text=m } } }
res=r.map { ResultItem(it,ns[0].toString()) }.toMutableList(); ra.update(res); b.tvStatus.text="Grp: "+res.size } }.setNegativeButton("Cancel",null).show() }
private fun sh() { if(spid<=0){ t("Pick app"); return }
val i=EditText(this).apply { hint="Speed"; inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText("2.0") }
AlertDialog.Builder(this).setTitle("Speedhack").setMessage("On: "+sname).setView(i)
.setPositiveButton("Apply") { _, _ -> val s=i.text.toString().toDoubleOrNull()?:1.0; aps(s) }
.setNeutralButton("Remove") { _, _ -> rms() }.setNegativeButton("Cancel",null).show() }
private fun aps(s: Double) { val pk=sname.substringBefore(":").trim()
lifecycleScope.launch { b.tvStatus.text="Speedhack..."
val ok=withContext(Dispatchers.IO) { val l=applicationInfo.nativeLibraryDir+"/libspeedhack.so"
if(!RootHelper.installSpeedhackLib(l)) "Fail" else RootHelper.applySpeedhack(pk,s) }
b.tvStatus.text="Sh: "+ok; t("Speed "+s+"x - restart game") } }
private fun rms() { val pk=sname.substringBefore(":").trim()
lifecycleScope.launch(Dispatchers.IO) { RootHelper.removeSpeedhack(pk); withContext(Dispatchers.Main) { t("Removed") } } }
private fun load() { lifecycleScope.launch { b.tvStatus.text="Loading..."
val l=withContext(Dispatchers.IO) { MemoryEngine.listProcesses() }
all=l; pa.update(l); b.tvStatus.text=l.size.toString()+" procs" } }
private fun sp(p: MemoryEngine.ProcessInfo) { spid=p.pid; sname=p.name; sarch=p.arch
b.tvSelected.text=p.name+" ["+p.pid+"] ["+p.arch+"]"; b.tvStatus.text="Ready"; res.clear(); ra.update(res) }
private fun sc(m: Int) { if(spid<=0){ t("Pick app"); return }
val v=b.etValue.text.toString().trim(); if(v.isEmpty()){ t("Value"); return }
lifecycleScope.launch { b.tvStatus.text="Scan..."
val r=withContext(Dispatchers.IO) { MemoryEngine.scan(spid,v,ct,m) { m2 -> lifecycleScope.launch(Dispatchers.Main) { b.tvStatus.text=m2 } } }
res=r.map { ResultItem(it,MemoryEngine.readValueString(spid,it,ct)) }.toMutableList()
if(res.size>500) res=res.take(500).toMutableList(); ra.update(res); b.tvStatus.text="Res: "+res.size } }
private fun fm() { if(res.isEmpty()){ t("Search"); return }
val o=arrayOf("Equal","Greater","Less","Changed","Unchanged","Increased","Decreased")
AlertDialog.Builder(this).setTitle("Filter").setItems(o) { _, w -> afl(w) }.setNegativeButton("Cancel",null).show() }
private fun afl(m: Int) { val v=b.etValue.text.toString().trim().ifEmpty { "0" }
lifecycleScope.launch { b.tvStatus.text="Filter..."
val ad=res.map { it.address }
val r=withContext(Dispatchers.IO) { MemoryEngine.filter(spid,ad,v,ct,m) }
res=r.map { ResultItem(it,MemoryEngine.readValueString(spid,it,ct)) }.toMutableList()
if(res.size>500) res=res.take(500).toMutableList(); ra.update(res); b.tvStatus.text="After: "+res.size } }
private fun wa() { if(res.isEmpty()){ t("Search"); return }
val nv=b.etNewValue.text.toString().trim(); if(nv.isEmpty()){ t("Value"); return }
lifecycleScope.launch { var c=0
withContext(Dispatchers.IO) { for(r in res) if(MemoryEngine.writeValue(spid,r.address,nv,ct)){ r.value=nv; c++ } }
ra.notifyDataSetChanged(); t("Mod "+c) } }
private fun ws() { val s=res.filter { it.selected }; if(s.isEmpty()){ t("None sel"); return }
val nv=b.etNewValue.text.toString().trim(); if(nv.isEmpty()){ t("Value"); return }
lifecycleScope.launch { var c=0
withContext(Dispatchers.IO) { for(r in s) if(MemoryEngine.writeValue(spid,r.address,nv,ct)){ r.value=nv; c++ } }
ra.notifyDataSetChanged(); t("Mod "+c) } }
private fun tf() { if(res.isEmpty()){ t("Search"); return }
if(fj?.isActive==true){ fj?.cancel(); fj=null; b.btnFreeze.text="Freeze"; t("Stop"); return }
val f=res.filter { it.selected }; if(f.isEmpty()){ t("Select"); return }
b.btnFreeze.text="Stop"; t("Freeze "+f.size)
fj=lifecycleScope.launch(Dispatchers.IO) { while(isActive) { for(r in f) MemoryEngine.writeValue(spid,r.address,r.value,ct); delay(200) } } }
private fun ed(p: Int) { val r=res[p]
val i=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED; setText(r.value); setSelection(r.value.length) }
AlertDialog.Builder(this).setTitle("Edit "+r.address.toString(16).uppercase(Locale.ROOT)).setView(i)
.setPositiveButton("Edit") { _, _ -> val nv=i.text.toString()
lifecycleScope.launch(Dispatchers.IO) { MemoryEngine.writeValue(spid,r.address,nv,ct); r.value=nv; withContext(Dispatchers.Main) { ra.notifyItemChanged(p) } } }
.setNeutralButton("Hex") { _, _ -> sx(r.address) }.setNegativeButton("Cancel",null).show() }
private fun sx(a: Long) { lifecycleScope.launch {
val d=withContext(Dispatchers.IO) { MemoryEngine.hexDump(spid,a,256) }
AlertDialog.Builder(this@MainActivity).setTitle("Hex "+a.toString(16).uppercase(Locale.ROOT)).setMessage(d).setPositiveButton("OK",null).show() } }
private fun sm() { val o=arrayOf("Save","Load","Show")
AlertDialog.Builder(this).setTitle("List").setItems(o) { _, w -> when(w){ 0->sv(); 1->ld(); 2->shw() } }.setNegativeButton("Cancel",null).show() }
private fun sv() { if(res.isEmpty()){ t("No"); return }
val i=EditText(this).apply { hint="Name"; setText("list_"+System.currentTimeMillis()/1000) }
AlertDialog.Builder(this).setTitle("Save").setView(i).setPositiveButton("Save") { _, _ ->
val n=i.text.toString().ifEmpty { "list" }
val f=File(getExternalFilesDir(null),n+".s7x")
val sb=StringBuilder(); sb.append("#S7xEngine v5\n#TYPE:"+ct+"\n")
for(r in res) sb.append(r.address).append(",").append(r.value).append(",").append(if(r.frozen) 1 else 0).append("\n")
f.writeText(sb.toString()); t("Saved "+f.name) }.setNegativeButton("Cancel",null).show() }
private fun ld() { val d=getExternalFilesDir(null)?:return
val fs=d.listFiles { f -> f.name.endsWith(".s7x") }?:return
if(fs.isEmpty()){ t("None"); return }
AlertDialog.Builder(this).setTitle("Load").setItems(fs.map { it.name }.toTypedArray()) { _, w ->
val c=fs[w].readText(); val nr=mutableListOf<ResultItem>(); var ty=MemoryEngine.TYPE_DWORD
for(l in c.lines()){ if(l.startsWith("#TYPE:")) ty=l.substringAfter(":").toIntOrNull()?:ty; else if(l.startsWith("#")) continue
else { val p=l.split(","); if(p.size>=2){ val a=p[0].toLongOrNull()?:continue; nr.add(ResultItem(a,p[1],p.size>2&&p[2]=="1")) } } }
ct=ty; b.spType.setSelection(ty); res=nr; ra.update(res); t("Loaded "+res.size) }.setNegativeButton("Cancel",null).show() }
private fun shw() { if(res.isEmpty()){ t("No"); return }
AlertDialog.Builder(this).setTitle("List ("+res.size+")").setItems(res.map { "0x"+it.address.toString(16).uppercase(Locale.ROOT)+" = "+it.value }.toTypedArray(), null).setPositiveButton("Close",null).show() }
private fun sr() { if(spid<=0){ t("Pick"); return }
lifecycleScope.launch { val m=withContext(Dispatchers.IO) { MemoryEngine.getMaps(spid) }
AlertDialog.Builder(this).setTitle("Regions ("+m.size+")").setMessage(m.take(100).joinToString("\n") { "0x"+it.start.toString(16)+"-0x"+it.end.toString(16)+" "+it.perms }).setPositiveButton("Close",null).show() } }
private fun t(m: String)=Toast.makeText(this,m,Toast.LENGTH_SHORT).show() }

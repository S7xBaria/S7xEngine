package com.s7x.engine

object MemoryEngine {
    init { System.loadLibrary("s7xengine") }

    const val TYPE_BYTE = 0
    const val TYPE_WORD = 1
    const val TYPE_DWORD = 2
    const val TYPE_QWORD = 3
    const val TYPE_FLOAT = 4
    const val TYPE_DOUBLE = 5

    const val MODE_EQUAL = 0
    const val MODE_GREATER = 1
    const val MODE_LESS = 2
    const val MODE_RANGE = 3

    private external fun nativeListProcesses(): Array<String>?
    private external fun nativeGetMaps(pid: Int): Array<String>?
    private external fun nativeGetPidFromPackage(p: String): Int
    private external fun nativeScanValue(pid: Int, s: Long, e: Long, v: String, t: Int, m: Int): LongArray?
    private external fun nativeFilterValue(pid: Int, a: LongArray, v: String, t: Int, m: Int): LongArray?
    private external fun nativeWriteValue(pid: Int, a: Long, v: String, t: Int): Boolean
    private external fun nativeReadValueString(pid: Int, a: Long, t: Int): String
    private external fun nativeReadValueLong(pid: Int, a: Long, t: Int): Long
    private external fun nativeHexDump(pid: Int, a: Long, s: Int): String

    data class ProcessInfo(val pid: Int, val name: String, val arch: String)

    fun listProcesses(): List<ProcessInfo> {
        val r = nativeListProcesses() ?: return emptyList()
        return r.mapNotNull {
            val p = it.split("|", limit = 3)
            if (p.size < 3) null
            else try { ProcessInfo(p[0].toInt(), p[1].trim(), p[2].trim()) } catch (e: Exception) { null }
        }.sortedBy { it.name }
    }

    fun getPid(p: String) = nativeGetPidFromPackage(p)
    fun readValueString(pid: Int, a: Long, t: Int) = nativeReadValueString(pid, a, t)
    fun readValueLong(pid: Int, a: Long, t: Int) = nativeReadValueLong(pid, a, t)
    fun hexDump(pid: Int, a: Long, s: Int) = nativeHexDump(pid, a, s)

    fun getMaps(pid: Int): List<MemoryMap> {
        val r = nativeGetMaps(pid) ?: return emptyList()
        val res = mutableListOf<MemoryMap>()
        for (l in r) {
            val p = l.trim().split(" ").filter { it.isNotEmpty() }
            if (p.size < 2) continue
            val rp = p[0].split("-")
            if (rp.size != 2) continue
            try { res.add(MemoryMap(rp[0].toLong(16), rp[1].toLong(16), p[1],
                if (p.size > 5) p.subList(5, p.size).joinToString(" ") else "")) } catch (e: Exception) {}
        }
        return res
    }

    fun scan(pid: Int, v: String, t: Int, m: Int, prog: (String) -> Unit = {}): List<Long> {
        val mp = getMaps(pid)
        val res = mutableListOf<Long>()
        for ((i, mm) in mp.withIndex()) {
            prog("Scan " + mm.name.ifEmpty { "?" } + " (" + (i + 1) + "/" + mp.size + ")")
            val f = nativeScanValue(pid, mm.start, mm.end, v, t, m) ?: continue
            res.addAll(f.toList())
            if (res.size >= 100000) break
        }
        return res
    }

    fun filter(pid: Int, a: List<Long>, v: String, t: Int, m: Int): List<Long> {
        val r = nativeFilterValue(pid, a.toLongArray(), v, t, m) ?: return emptyList()
        return r.toList()
    }

    fun writeValue(pid: Int, a: Long, v: String, t: Int) = nativeWriteValue(pid, a, v, t)
}

data class MemoryMap(val start: Long, val end: Long, val perms: String, val name: String)
data class ResultItem(val address: Long, var value: String, var frozen: Boolean = false, var selected: Boolean = false)

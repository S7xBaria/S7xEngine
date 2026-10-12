package com.s7x.engine

object RootHelper {
    fun isRooted(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val o = p.inputStream.bufferedReader().readText()
            p.waitFor()
            o.contains("uid=0")
        } catch (e: Exception) { false }
    }
}

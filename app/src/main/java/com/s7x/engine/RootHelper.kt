package com.s7x.engine
object RootHelper {
fun isRooted(): Boolean { return try { val p=Runtime.getRuntime().exec(arrayOf("su","-c","id"))
val o=p.inputStream.bufferedReader().readText(); p.waitFor(); o.contains("uid=0") } catch(e:Exception){ false } }
fun exec(c: String): String { return try { val p=Runtime.getRuntime().exec(arrayOf("su","-c",c))
val o=p.inputStream.bufferedReader().readText(); val e=p.errorStream.bufferedReader().readText()
p.waitFor(); if(o.isEmpty()) e else o } catch(e:Exception){ "ERR:"+e.message } }
fun applySpeedhack(pkg: String, speed: Double): String { exec("am force-stop "+pkg); Thread.sleep(500)
return exec("setprop wrap."+pkg+" 'LD_PRELOAD=/data/local/tmp/libs7xspeed.so S7X_SPEED="+speed+"'") }
fun removeSpeedhack(pkg: String): String = exec("setprop wrap."+pkg+" ''")
fun installSpeedhackLib(p: String): Boolean { val r=exec("cp "+p+" /data/local/tmp/libs7xspeed.so && chmod 755 /data/local/tmp/libs7xspeed.so")
return !r.contains("ERR")&&!r.contains("No such") } }

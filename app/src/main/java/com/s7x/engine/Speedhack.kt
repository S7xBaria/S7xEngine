package com.s7x.engine
object Speedhack {
init { try { System.loadLibrary("speedhack") } catch(e:Throwable){} }
external fun setSpeed(s: Double)
external fun getSpeed(): Double }

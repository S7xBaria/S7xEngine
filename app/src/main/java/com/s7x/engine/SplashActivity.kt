package com.s7x.engine
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class SplashActivity : AppCompatActivity() {
override fun onCreate(s: Bundle?) { super.onCreate(s); setContentView(R.layout.activity_splash)
val l = findViewById<ImageView>(R.id.ivLogo)
val t = findViewById<TextView>(R.id.tvSplashTitle)
val u = findViewById<TextView>(R.id.tvSplashSub)
val a = AnimationUtils.loadAnimation(this, android.R.anim.fade_in); a.duration=1500
l.startAnimation(a); t.startAnimation(a); u.startAnimation(a)
android.os.Handler(mainLooper).postDelayed({
startActivity(Intent(this, MainActivity::class.java))
overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
finish()}, 2200) } }

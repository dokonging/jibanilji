package com.jibanilji.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** 앱 전체 설정: 화면 모드(시스템/라이트/다크) */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        apply(mode(this))
    }

    companion object {
        const val MODE_SYSTEM = 0
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2

        fun mode(ctx: Context) = ctx.getSharedPreferences("ui", 0).getInt("mode", MODE_SYSTEM)

        fun setMode(ctx: Context, m: Int) {
            if (m == mode(ctx)) return
            ctx.getSharedPreferences("ui", 0).edit().putInt("mode", m).apply()
            apply(m)
        }

        private fun apply(m: Int) = AppCompatDelegate.setDefaultNightMode(
            when (m) {
                MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}

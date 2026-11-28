package com.goldanalyzer

import android.content.Context

/**
 * وضعیت انقضای سیگنال‌های انس را بین اجرای برنامه‌ها نگه می‌دارد.
 * انقضا فقط برای همان کندل معتبر است؛ با رسیدن زمان کندل جدید، کلید قبلی
 * دیگر با زمان کندل جدید تطبیق ندارد و سیگنال تازه می‌تواند بررسی شود.
 */
object XauSignalLifecycleStore {

    private const val PREFS_NAME = "xau_signal_lifecycle"

    fun getExpiredReason(
        context: Context,
        lifecycleKey: String,
        candleTime: Long
    ): String? {
        val preferences = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val savedCandleTime = preferences.getLong(
            "${lifecycleKey}_candle_time",
            Long.MIN_VALUE
        )
        if (savedCandleTime != candleTime) return null
        return preferences.getString("${lifecycleKey}_reason", null)
    }

    fun markExpired(
        context: Context,
        lifecycleKey: String,
        candleTime: Long,
        reason: String
    ) {
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putLong("${lifecycleKey}_candle_time", candleTime)
            .putString("${lifecycleKey}_reason", reason)
            .apply()
    }
}

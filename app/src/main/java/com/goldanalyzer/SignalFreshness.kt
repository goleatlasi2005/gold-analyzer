
package com.goldanalyzer

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

object SignalFreshness {

    private const val MAX_QUOTE_AGE_MILLIS = 180_000L
    private val TEHRAN_ZONE = ZoneId.of("Asia/Tehran")

    /**
     * تبدیل تاریخ و ساعت آخرین معامله عیار به زمان واقعی.
     * قالب ساعت TSETMC به شکل HHmmss است.
     */
    fun ayiarObservedAtMillis(
        dateValue: Long,
        timeValue: Long
    ): Long? {
        return try {
            if (dateValue <= 0L || timeValue < 0L) {
                return null
            }

            val dateText = dateValue.toString().padStart(8, '0')

            if (dateText.length != 8) return null

            val year = dateText.substring(0, 4).toInt()
            val month = dateText.substring(4, 6).toInt()
            val day = dateText.substring(6, 8).toInt()

            val hour = (timeValue / 10_000).toInt()
            val minute = ((timeValue / 100) % 100).toInt()
            val second = (timeValue % 100).toInt()

            if (
                hour !in 0..23 ||
                minute !in 0..59 ||
                second !in 0..59
            ) {
                return null
            }

            LocalDate.of(year, month, day)
                .atTime(hour, minute, second)
                .atZone(TEHRAN_ZONE)
                .toInstant()
                .toEpochMilli()

        } catch (_: Exception) {
            null
        }
    }

    /**
     * اگر سیگنال قابل اجرا نباشد، علت را برمی‌گرداند.
     * خروجی null یعنی سیگنال از نظر قیمت و تازگی معتبر است.
     */
    fun inactiveReason(
        signal: TradingSignal,
        livePrice: Double?,
        observedAtMillis: Long?,
        quoteFresh: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): String? {

        if (signal.type == SignalType.HOLD) {
            return null
        }

        if (
            !signal.entryPrice.isFinite() ||
            signal.entryPrice <= 0.0
        ) {
            return "قیمت مرجع ورود سیگنال معتبر نیست."
        }

        if (
            !quoteFresh ||
            livePrice == null ||
            !livePrice.isFinite() ||
            livePrice <= 0.0
        ) {
            return "قیمت لحظه‌ای تازه و معتبر در دسترس نیست؛ این سیگنال فعلاً قابل اجرا نیست."
        }

        val observedAt = observedAtMillis
            ?: return "زمان ثبت قیمت لحظه‌ای مشخص نیست؛ سیگنال فعلاً غیرفعال است."

        if (
            observedAt <= 0L ||
            observedAt > nowMillis ||
            nowMillis - observedAt > MAX_QUOTE_AGE_MILLIS
        ) {
            return "قیمت دریافتی قدیمی است؛ برای اعتبارسنجی سیگنال باید قیمت تازه دریافت شود."
        }

        val atr = signal.atr.takeIf {
            it.isFinite() && it > 0.0
        } ?: (
            abs(signal.entryPrice - signal.stopLoss) / 1.5
        ).takeIf {
            it.isFinite() && it > 0.0
        }

        if (atr == null) {
            return "دامنه نوسان معتبر برای کنترل قیمت سیگنال در دسترس نیست."
        }

        // حد مجاز فاصله: حداقل ۰٫۱۵٪ قیمت،
        // متناسب با ATR و حداکثر ۰٫۵٪ قیمت.
        val maximumDeviation = minOf(
            maxOf(
                atr * 0.5,
                livePrice * 0.0015
            ),
            livePrice * 0.005
        )

        val actualDeviation = abs(
            livePrice - signal.entryPrice
        )

        if (actualDeviation > maximumDeviation) {
            return "قیمت فعلی از محدوده مجاز ورود فاصله گرفته است؛ سیگنال قبلی منقضی شده و نباید با قیمت ورود قبلی اجرا شود."
        }

        return null
    }
}

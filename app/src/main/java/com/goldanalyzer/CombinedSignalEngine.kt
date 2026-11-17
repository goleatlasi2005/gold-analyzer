
package com.goldanalyzer

import kotlin.math.abs

data class WeightedTradingSignal(
    val timeframeLabel: String,
    val weightPercent: Int,
    val signal: TradingSignal
)

object CombinedSignalEngine {

    private const val BUY_THRESHOLD = 40
    private const val SELL_THRESHOLD = -40

    /**
     * ترکیب سه سیگنال مستقل با وزن‌های مشخص.
     *
     * short-term:
     * 5m  = 15%
     * 15m = 30%
     * 30m = 55%
     *
     * medium-term:
     * 1h   = 15%
     * 4h   = 30%
     * daily = 55%
     *
     * خروجی قیمت ورود و ATR از تایم‌فریم اصلی گرفته می‌شود.
     */
    fun combine(
        signals: List<WeightedTradingSignal>,
        primaryTimeframeLabel: String
    ): TradingSignal? {

        if (signals.size != 3) return null

        if (signals.any { it.weightPercent <= 0 }) {
            return null
        }

        if (signals.sumOf { it.weightPercent } != 100) {
            return null
        }

        if (signals.map { it.timeframeLabel }.distinct().size != 3) {
            return null
        }

        val primary = signals.firstOrNull {
            it.timeframeLabel == primaryTimeframeLabel
        } ?: return null

        // امتیاز نهایی بر اساس وزن هر تایم‌فریم
        val weightedScore = signals.sumOf { item ->
            item.signal.score * item.weightPercent
        } / 100.0

        val finalScore = weightedScore
            .toInt()
            .coerceIn(-100, 100)

        val finalType = when {
            finalScore >= BUY_THRESHOLD -> SignalType.BUY
            finalScore <= SELL_THRESHOLD -> SignalType.SELL
            else -> SignalType.HOLD
        }

        // میزان توافق تایم‌فریم‌ها با جهت نهایی
        val agreementWeight = when (finalType) {
            SignalType.BUY -> signals
                .filter { it.signal.type == SignalType.BUY }
                .sumOf { it.weightPercent }

            SignalType.SELL -> signals
                .filter { it.signal.type == SignalType.SELL }
                .sumOf { it.weightPercent }

            SignalType.HOLD -> signals
                .filter { it.signal.type == SignalType.HOLD }
                .sumOf { it.weightPercent }
        }

        // اعتبار ترکیبی از قدرت امتیاز و میزان توافق
        val confidence = (
            abs(finalScore) * 0.65 +
                agreementWeight * 0.35
            ).toInt().coerceIn(0, 100)

        val reasons = mutableListOf<String>()

        reasons.add("امتیاز نهایی ترکیبی: $finalScore از ۱۰۰")
        reasons.add("میزان توافق تایم‌فریم‌ها: $agreementWeight درصد")
        reasons.add("اعتبار محاسبه‌شده: $confidence درصد")

        signals.forEach { item ->
            val direction = when (item.signal.type) {
                SignalType.BUY -> "خرید"
                SignalType.SELL -> "فروش"
                SignalType.HOLD -> "صبر"
            }

            val contribution = (
                item.signal.score * item.weightPercent / 100.0
                )

            reasons.add(
                "${item.timeframeLabel}: $direction، " +
                    "امتیاز ${item.signal.score}، " +
                    "وزن ${item.weightPercent}٪، " +
                    "اثر وزنی ${"%.1f".format(java.util.Locale.US, contribution)}"
            )
        }

        if (agreementWeight < 70) {
            reasons.add(
                "بین تایم‌فریم‌ها اختلاف جهت وجود دارد؛ " +
                    "در تصمیم‌گیری احتیاط شود."
            )
        } else {
            reasons.add(
                "اکثر وزن تحلیلی تایم‌فریم‌ها با جهت نهایی موافق است."
            )
        }

        // قیمت و ATR از تایم‌فریم اصلی گرفته می‌شوند.
        val entryPrice = primary.signal.entryPrice
        val atr = primary.signal.atr

        if (
            !entryPrice.isFinite() ||
            entryPrice <= 0.0 ||
            !atr.isFinite() ||
            atr < 0.0
        ) {
            return null
        }

        val stopDistance = atr * 1.5

        val stopLoss = when (finalType) {
            SignalType.BUY -> entryPrice - stopDistance
            SignalType.SELL -> entryPrice + stopDistance
            SignalType.HOLD -> 0.0
        }

        val takeProfit1 = when (finalType) {
            SignalType.BUY -> entryPrice + stopDistance
            SignalType.SELL -> entryPrice - stopDistance
            SignalType.HOLD -> 0.0
        }

        val takeProfit2 = when (finalType) {
            SignalType.BUY -> entryPrice + stopDistance * 2.0
            SignalType.SELL -> entryPrice - stopDistance * 2.0
            SignalType.HOLD -> 0.0
        }

        val takeProfit3 = when (finalType) {
            SignalType.BUY -> entryPrice + stopDistance * 3.0
            SignalType.SELL -> entryPrice - stopDistance * 3.0
            SignalType.HOLD -> 0.0
        }

        val trend = when (finalType) {
            SignalType.BUY -> "تمایل ترکیبی صعودی"
            SignalType.SELL -> "تمایل ترکیبی نزولی"
            SignalType.HOLD -> "روند ترکیبی نامشخص یا خنثی"
        }

        if (finalType == SignalType.HOLD) {
            reasons.add(
                "قدرت یا توافق سیگنال‌ها برای ورود کافی نیست؛ فعلاً صبر."
            )
        }

        return TradingSignal(
            type = finalType,
            score = finalScore,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit1 = takeProfit1,
            takeProfit2 = takeProfit2,
            takeProfit3 = takeProfit3,
            riskRewardRatio = if (finalType == SignalType.HOLD) {
                0.0
            } else {
                3.0
            },
            confidencePercent = confidence,
            trend = trend,
            adx = primary.signal.adx,
            atr = atr,
            reasons = reasons
        )
    }
}

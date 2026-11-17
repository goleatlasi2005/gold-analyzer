
package com.goldanalyzer

import kotlin.math.abs
import kotlin.math.roundToInt

data class CombinedSignalAnalysis(
    val signal: TradingSignal?,
    val availableTimeframes: List<String>,
    val missingTimeframes: List<String>
)

object MultiTimeframeSignalEngine {

    private data class TimeframeConfig(
        val label: String,
        val minimumCandles: Int,
        val longEmaPeriod: Int
    )

    private val shortTermConfig = listOf(
        TimeframeConfig("۵ دقیقه", 200, 200),
        TimeframeConfig("۱۵ دقیقه", 200, 200),
        TimeframeConfig("۳۰ دقیقه", 200, 200)
    )

    private val mediumTermConfig = listOf(
        TimeframeConfig("۱ ساعت", 130, 100),
        TimeframeConfig("۴ ساعت", 130, 100),
        TimeframeConfig("روزانه", 200, 200)
    )

    fun analyzeShortTerm(
        candlesByTimeframe: Map<String, List<Candle>>
    ): CombinedSignalAnalysis {
        return analyze(candlesByTimeframe, shortTermConfig, "۵ دقیقه")
    }

    fun analyzeMediumTerm(
        candlesByTimeframe: Map<String, List<Candle>>
    ): CombinedSignalAnalysis {
        return analyze(candlesByTimeframe, mediumTermConfig, "۱ ساعت")
    }

    private fun analyze(
        candlesByTimeframe: Map<String, List<Candle>>,
        configs: List<TimeframeConfig>,
        referenceTimeframe: String
    ): CombinedSignalAnalysis {

        val now = System.currentTimeMillis()
        val signals = mutableListOf<Pair<TimeframeConfig, TradingSignal>>()
        val missing = mutableListOf<String>()

        for (config in configs) {
            val completedCandles = candlesByTimeframe[config.label]
                .orEmpty()
                .filter { it.closeTime <= now }
                .distinctBy { it.openTime }
                .sortedBy { it.openTime }

            val signal = SignalEngine.analyze(
                candles = completedCandles,
                nowMillis = now,
                minimumCandles = config.minimumCandles,
                longEmaPeriod = config.longEmaPeriod
            )

            if (signal == null) {
                missing.add(config.label)
            } else {
                signals.add(config to signal)
            }
        }

        if (missing.isNotEmpty() || signals.size != configs.size) {
            return CombinedSignalAnalysis(
                signal = null,
                availableTimeframes = signals.map { it.first.label },
                missingTimeframes = missing
            )
        }

        val averageScore = signals
            .map { it.second.score }
            .average()
            .roundToInt()
            .coerceIn(-100, 100)

        val type = when {
            averageScore >= 40 -> SignalType.BUY
            averageScore <= -40 -> SignalType.SELL
            else -> SignalType.HOLD
        }

        val referenceSignal = signals
            .firstOrNull { it.first.label == referenceTimeframe }
            ?.second
            ?: signals.first().second

        val entryPrice = referenceSignal.entryPrice
        val stopDistance = referenceSignal.atr * 1.5

        val stopLoss = when (type) {
            SignalType.BUY -> entryPrice - stopDistance
            SignalType.SELL -> entryPrice + stopDistance
            SignalType.HOLD -> 0.0
        }

        val takeProfit1 = when (type) {
            SignalType.BUY -> entryPrice + stopDistance
            SignalType.SELL -> entryPrice - stopDistance
            SignalType.HOLD -> 0.0
        }

        val takeProfit2 = when (type) {
            SignalType.BUY -> entryPrice + stopDistance * 2.0
            SignalType.SELL -> entryPrice - stopDistance * 2.0
            SignalType.HOLD -> 0.0
        }

        val takeProfit3 = when (type) {
            SignalType.BUY -> entryPrice + stopDistance * 3.0
            SignalType.SELL -> entryPrice - stopDistance * 3.0
            SignalType.HOLD -> 0.0
        }

        val reasons = mutableListOf<String>()

        reasons.add(
            "امتیاز ترکیبی از میانگین امتیاز سه تایم‌فریم محاسبه شده است."
        )

        signals.forEach { (config, signal) ->
            reasons.add(
                "${config.label}: امتیاز ${signal.score} از ۱۰۰، روند ${signal.trend}"
            )
        }

        val buyCount = signals.count { it.second.type == SignalType.BUY }
        val sellCount = signals.count { it.second.type == SignalType.SELL }

        reasons.add(
            "توافق تایم‌فریم‌ها: $buyCount خرید، $sellCount فروش و ${3 - buyCount - sellCount} صبر"
        )

        if (type == SignalType.HOLD) {
            reasons.add(
                "توافق یا قدرت سیگنال‌ها برای تصمیم خرید یا فروش کافی نیست."
            )
        }

        val averageAtr = signals
            .map { it.second.atr }
            .average()

        val trend = when {
            buyCount == 3 -> "صعودی هم‌جهت"
            sellCount == 3 -> "نزولی هم‌جهت"
            buyCount > sellCount -> "تمایل صعودی با اختلاف تایم‌فریم‌ها"
            sellCount > buyCount -> "تمایل نزولی با اختلاف تایم‌فریم‌ها"
            else -> "ناهماهنگ و خنثی"
        }

        val combinedSignal = TradingSignal(
            type = type,
            score = averageScore,
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit1 = takeProfit1,
            takeProfit2 = takeProfit2,
            takeProfit3 = takeProfit3,
            riskRewardRatio = if (type == SignalType.HOLD) 0.0 else 3.0,
            confidencePercent = abs(averageScore).coerceIn(0, 100),
            reasons = reasons,
            trend = trend,
            adx = signals.mapNotNull { it.second.adx }.average()
                .takeIf { it.isFinite() },
            atr = averageAtr
        )

        return CombinedSignalAnalysis(
            signal = combinedSignal,
            availableTimeframes = signals.map { it.first.label },
            missingTimeframes = emptyList()
        )
    }
}

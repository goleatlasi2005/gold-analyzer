
package com.goldanalyzer

import kotlin.math.abs

enum class SignalType {
    BUY,
    SELL,
    HOLD
}

data class TradingSignal(
    val type: SignalType,
    val score: Int,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val reasons: List<String>,
    val takeProfit3: Double = 0.0,
    val riskRewardRatio: Double = 0.0,
    val confidencePercent: Int = 0,
    val trend: String = "نامشخص",
    val adx: Double? = null,
    val atr: Double = 0.0
)

object SignalEngine {

    fun analyze(
        candles: List<Candle>,
        nowMillis: Long = System.currentTimeMillis(),
        minimumCandles: Int = 200,
        longEmaPeriod: Int = 200
    ): TradingSignal? {

        val validCandles = candles
            .filter { it.closeTime <= nowMillis }
            .filter {
                it.open > 0.0 &&
                it.high > 0.0 &&
                it.low > 0.0 &&
                it.close > 0.0 &&
                it.high >= it.low &&
                it.volume >= 0.0
            }
            .distinctBy { it.openTime }
            .sortedBy { it.openTime }

        val requiredCount = maxOf(minimumCandles, longEmaPeriod)

        if (validCandles.size < requiredCount) return null

        val closes = validCandles.map { it.close }
        val last = validCandles.lastIndex
        val current = validCandles[last]
        val price = current.close

        val ema20 = TechnicalIndicators.ema(closes, 20)[last]
            ?: return null
        val ema50 = TechnicalIndicators.ema(closes, 50)[last]
            ?: return null
        val emaLong = TechnicalIndicators.ema(
            closes,
            longEmaPeriod
        )[last] ?: return null

        val rsi = TechnicalIndicators.rsi(closes)[last]
            ?: return null

        val macd = TechnicalIndicators.macd(closes)[last]
            ?: return null

        val atr = TechnicalIndicators.atr(validCandles)[last]
            ?: return null

        val bands = TechnicalIndicators.bollingerBands(closes)[last]
            ?: return null

        val stochastic = TechnicalIndicators.stochastic(validCandles)
        val currentStochastic = stochastic[last] ?: return null
        val previousStochastic = stochastic[last - 1]

        val ichimoku = AdvancedIndicators.ichimoku(validCandles)[last]
        val fibonacci = AdvancedIndicators.fibonacci(validCandles)

        if (price <= 0.0 || atr <= 0.0 || !atr.isFinite()) {
            return null
        }

        val adx = calculateAdx(validCandles)
        val structure = detectMarketStructure(validCandles)

        var score = 0
        val reasons = mutableListOf<String>()

        when {
            rsi >= 55.0 && rsi < 70.0 -> {
                score += 8
                reasons.add("RSI مومنتوم صعودی را تأیید می‌کند")
            }
            rsi > 30.0 && rsi <= 45.0 -> {
                score -= 8
                reasons.add("RSI مومنتوم نزولی را تأیید می‌کند")
            }
            rsi >= 70.0 -> {
                score -= 4
                reasons.add("RSI وارد محدوده اشباع خرید شده است")
            }
            rsi <= 30.0 -> {
                score += 4
                reasons.add("RSI وارد محدوده اشباع فروش شده است")
            }
        }

        when {
            ema20 > ema50 && ema50 > emaLong -> {
                score += 15
                reasons.add(
                    "چیدمان EMA20، EMA50 و EMA$longEmaPeriod صعودی است"
                )
            }
            ema20 < ema50 && ema50 < emaLong -> {
                score -= 15
                reasons.add(
                    "چیدمان EMA20، EMA50 و EMA$longEmaPeriod نزولی است"
                )
            }
            ema20 > ema50 -> {
                score += 6
                reasons.add("EMA20 بالاتر از EMA50 است")
            }
            ema20 < ema50 -> {
                score -= 6
                reasons.add("EMA20 پایین‌تر از EMA50 است")
            }
        }

        if (price > ema20) {
            score += 6
            reasons.add("قیمت بالاتر از EMA20 قرار دارد")
        } else if (price < ema20) {
            score -= 6
            reasons.add("قیمت پایین‌تر از EMA20 قرار دارد")
        }

        if (macd.macd > macd.signal) {
            score += 10
            reasons.add("MACD وضعیت صعودی دارد")
        } else if (macd.macd < macd.signal) {
            score -= 10
            reasons.add("MACD وضعیت نزولی دارد")
        }

        if (macd.histogram > 0.0) {
            score += 4
            reasons.add("هیستوگرام MACD مثبت است")
        } else if (macd.histogram < 0.0) {
            score -= 4
            reasons.add("هیستوگرام MACD منفی است")
        }

        if (adx != null) {
            if (adx >= 25.0) {
                when {
                    ema20 > ema50 -> {
                        score += 8
                        reasons.add("ADX قدرت روند صعودی را تأیید می‌کند")
                    }
                    ema20 < ema50 -> {
                        score -= 8
                        reasons.add("ADX قدرت روند نزولی را تأیید می‌کند")
                    }
                }
            } else if (adx < 15.0) {
                reasons.add("قدرت روند پایین است و بازار حالت خنثی دارد")
            } else {
                reasons.add("قدرت روند متوسط است")
            }
        }

        val tenkan = ichimoku.tenkan
        val kijun = ichimoku.kijun
        val senkouA = ichimoku.senkouA
        val senkouB = ichimoku.senkouB

        if (tenkan != null && kijun != null) {
            if (tenkan > kijun) {
                score += 6
                reasons.add("تنکان بالاتر از کیجون است")
            } else if (tenkan < kijun) {
                score -= 6
                reasons.add("تنکان پایین‌تر از کیجون است")
            }
        }

        if (senkouA != null && senkouB != null) {
            val cloudTop = maxOf(senkouA, senkouB)
            val cloudBottom = minOf(senkouA, senkouB)

            when {
                price > cloudTop -> {
                    score += 6
                    reasons.add("قیمت بالاتر از ابر ایچیموکو است")
                }
                price < cloudBottom -> {
                    score -= 6
                    reasons.add("قیمت پایین‌تر از ابر ایچیموکو است")
                }
            }
        }

        when {
            price > bands.upper -> {
                score += 3
                reasons.add("قیمت بالاتر از باند بالایی بولینگر است")
            }
            price < bands.lower -> {
                score -= 3
                reasons.add("قیمت پایین‌تر از باند پایینی بولینگر است")
            }
            price > bands.middle -> {
                score += 3
                reasons.add("قیمت بالاتر از میانگین بولینگر است")
            }
            price < bands.middle -> {
                score -= 3
                reasons.add("قیمت پایین‌تر از میانگین بولینگر است")
            }
        }

        if (previousStochastic != null) {
            when {
                previousStochastic < 20.0 &&
                    currentStochastic >= 20.0 -> {
                    score += 6
                    reasons.add("استوکاستیک از اشباع فروش خارج شده است")
                }
                previousStochastic > 80.0 &&
                    currentStochastic <= 80.0 -> {
                    score -= 6
                    reasons.add("استوکاستیک از اشباع خرید خارج شده است")
                }
            }
        }

        if (fibonacci != null) {
            val nearest = fibonacci.levels.values.minByOrNull {
                abs(price - it)
            }

            if (nearest != null) {
                val tolerance = maxOf(atr * 0.35, price * 0.002)

                if (abs(price - nearest) <= tolerance) {
                    if (price >= nearest) {
                        score += 5
                        reasons.add("قیمت به سطح حمایتی فیبوناچی نزدیک است")
                    } else {
                        score -= 5
                        reasons.add("قیمت به سطح مقاومتی فیبوناچی نزدیک است")
                    }
                }
            }
        }

        val previousVolumes = validCandles
            .dropLast(1)
            .takeLast(20)
            .map { it.volume }

        if (previousVolumes.size == 20) {
            val averageVolume = previousVolumes.average()

            if (
                averageVolume > 0.0 &&
                current.volume > averageVolume * 1.2
            ) {
                when {
                    current.close > current.open -> {
                        score += 6
                        reasons.add("حجم معاملات و کندل صعودی تأییدکننده‌اند")
                    }
                    current.close < current.open -> {
                        score -= 6
                        reasons.add("حجم معاملات و کندل نزولی تأییدکننده‌اند")
                    }
                }
            }
        }

        when (structure) {
            "HH/HL" -> {
                score += 10
                reasons.add("ساختار بازار صعودی است: سقف و کف بالاتر")
            }
            "LH/LL" -> {
                score -= 10
                reasons.add("ساختار بازار نزولی است: سقف و کف پایین‌تر")
            }
            else -> {
                reasons.add("ساختار بازار هنوز جهت مشخصی ندارد")
            }
        }

        score = score.coerceIn(-100, 100)

        val type = when {
            score >= 40 -> SignalType.BUY
            score <= -40 -> SignalType.SELL
            else -> SignalType.HOLD
        }

        val trend = when {
            ema20 > ema50 && ema50 > emaLong &&
                (adx ?: 0.0) >= 20.0 -> "صعودی قوی"
            ema20 < ema50 && ema50 < emaLong &&
                (adx ?: 0.0) >= 20.0 -> "نزولی قوی"
            ema20 > ema50 -> "صعودی"
            ema20 < ema50 -> "نزولی"
            else -> "خنثی"
        }

        val stopDistance = atr * 1.5

        val stopLoss = when (type) {
            SignalType.BUY -> price - stopDistance
            SignalType.SELL -> price + stopDistance
            SignalType.HOLD -> 0.0
        }

        val takeProfit1 = when (type) {
            SignalType.BUY -> price + stopDistance
            SignalType.SELL -> price - stopDistance
            SignalType.HOLD -> 0.0
        }

        val takeProfit2 = when (type) {
            SignalType.BUY -> price + stopDistance * 2.0
            SignalType.SELL -> price - stopDistance * 2.0
            SignalType.HOLD -> 0.0
        }

        val takeProfit3 = when (type) {
            SignalType.BUY -> price + stopDistance * 3.0
            SignalType.SELL -> price - stopDistance * 3.0
            SignalType.HOLD -> 0.0
        }

        if (type == SignalType.HOLD) {
            reasons.add("قدرت سیگنال برای ورود کافی نیست؛ فعلاً صبر")
        }

        return TradingSignal(
            type = type,
            score = score,
            entryPrice = price,
            stopLoss = stopLoss,
            takeProfit1 = takeProfit1,
            takeProfit2 = takeProfit2,
            reasons = reasons,
            takeProfit3 = takeProfit3,
            riskRewardRatio = if (type == SignalType.HOLD) 0.0 else 3.0,
            confidencePercent = abs(score).coerceIn(0, 100),
            trend = trend,
            adx = adx,
            atr = atr
        )
    }

    private fun calculateAdx(
        candles: List<Candle>,
        period: Int = 14
    ): Double? {
        if (candles.size < period * 2 + 1) return null

        val dxValues = mutableListOf<Double>()

        for (end in period until candles.size) {
            var plusDmSum = 0.0
            var minusDmSum = 0.0
            var trSum = 0.0

            val start = end - period + 1

            for (i in start..end) {
                val current = candles[i]
                val previous = candles[i - 1]

                val upMove = current.high - previous.high
                val downMove = previous.low - current.low

                val plusDm = if (
                    upMove > downMove && upMove > 0.0
                ) upMove else 0.0

                val minusDm = if (
                    downMove > upMove && downMove > 0.0
                ) downMove else 0.0

                val trueRange = maxOf(
                    current.high - current.low,
                    abs(current.high - previous.close),
                    abs(current.low - previous.close)
                )

                plusDmSum += plusDm
                minusDmSum += minusDm
                trSum += trueRange
            }

            if (trSum <= 0.0) continue

            val plusDi = plusDmSum / trSum * 100.0
            val minusDi = minusDmSum / trSum * 100.0
            val denominator = plusDi + minusDi

            if (denominator > 0.0) {
                dxValues.add(
                    abs(plusDi - minusDi) / denominator * 100.0
                )
            }
        }

        return dxValues
            .takeLast(period)
            .takeIf { it.isNotEmpty() }
            ?.average()
    }

    private fun detectMarketStructure(
        candles: List<Candle>
    ): String {
        if (candles.size < 40) return "نامشخص"

        val recent = candles.takeLast(40)
        val firstHalf = recent.take(20)
        val secondHalf = recent.takeLast(20)

        val firstHigh = firstHalf.maxOf { it.high }
        val secondHigh = secondHalf.maxOf { it.high }
        val firstLow = firstHalf.minOf { it.low }
        val secondLow = secondHalf.minOf { it.low }

        return when {
            secondHigh > firstHigh && secondLow > firstLow ->
                "HH/HL"
            secondHigh < firstHigh && secondLow < firstLow ->
                "LH/LL"
            else -> "خنثی"
        }
    }
}

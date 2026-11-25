
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

        if (minimumCandles <= 0 || longEmaPeriod <= 0) {
            return null
        }

        val validCandles = candles
            .filter { it.closeTime <= nowMillis }
            .filter {
                it.open.isFinite() &&
                it.high.isFinite() &&
                it.low.isFinite() &&
                it.close.isFinite() &&
                it.volume.isFinite() &&
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

        if (
            price <= 0.0 ||
            atr <= 0.0 ||
            !price.isFinite() ||
            !atr.isFinite()
        ) {
            return null
        }

        val adx = calculateAdx(validCandles)
        val structure = detectMarketStructure(validCandles)

        var score = 0
        val reasons = mutableListOf<String>()

        // RSI
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

        // EMA trend
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

        // MACD
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

        // ADX
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

        // Ichimoku
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

        // Bollinger Bands
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

        // Stochastic
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

        // Fibonacci support and resistance
        if (fibonacci != null) {
            val nearestLevel = fibonacci.levels.values.minByOrNull {
                abs(price - it)
            }

            if (nearestLevel != null) {
                val tolerance = maxOf(atr * 0.35, price * 0.002)
                val distance = abs(price - nearestLevel)

                if (distance <= tolerance) {
                    when {
                        price > nearestLevel -> {
                            score += 5
                            reasons.add(
                                "قیمت به سطح حمایتی فیبوناچی نزدیک است"
                            )
                        }

                        price < nearestLevel -> {
                            score -= 5
                            reasons.add(
                                "قیمت به سطح مقاومتی فیبوناچی نزدیک است"
                            )
                        }

                        else -> {
                            reasons.add(
                                "قیمت روی یکی از سطوح فیبوناچی قرار دارد"
                            )
                        }
                    }
                }
            }
        }

        // Volume confirmation
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
                        reasons.add(
                            "حجم معاملات و کندل صعودی تأییدکننده‌اند"
                        )
                    }

                    current.close < current.open -> {
                        score -= 6
                        reasons.add(
                            "حجم معاملات و کندل نزولی تأییدکننده‌اند"
                        )
                    }
                }
            }
        }

        // Market structure
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

        // Risk management
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

    /**
     * Average Directional Index using Wilder smoothing.
     * ADX measures trend strength; EMA alignment determines direction.
     */
    private fun calculateAdx(
        candles: List<Candle>,
        period: Int = 14
    ): Double? {

        if (period <= 0 || candles.size < period * 2) {
            return null
        }

        val trueRanges = DoubleArray(candles.size)
        val positiveDm = DoubleArray(candles.size)
        val negativeDm = DoubleArray(candles.size)

        for (i in 1 until candles.size) {
            val current = candles[i]
            val previous = candles[i - 1]

            val upMove = current.high - previous.high
            val downMove = previous.low - current.low

            positiveDm[i] = if (
                upMove > downMove && upMove > 0.0
            ) {
                upMove
            } else {
                0.0
            }

            negativeDm[i] = if (
                downMove > upMove && downMove > 0.0
            ) {
                downMove
            } else {
                0.0
            }

            trueRanges[i] = maxOf(
                current.high - current.low,
                abs(current.high - previous.close),
                abs(current.low - previous.close)
            )

            if (
                !trueRanges[i].isFinite() ||
                !positiveDm[i].isFinite() ||
                !negativeDm[i].isFinite()
            ) {
                return null
            }
        }

        var smoothedTr = 0.0
        var smoothedPositiveDm = 0.0
        var smoothedNegativeDm = 0.0

        for (i in 1..period) {
            smoothedTr += trueRanges[i]
            smoothedPositiveDm += positiveDm[i]
            smoothedNegativeDm += negativeDm[i]
        }

        val dxValues = mutableListOf<Double>()

        for (i in period until candles.size) {

            if (i > period) {
                smoothedTr =
                    smoothedTr - smoothedTr / period + trueRanges[i]

                smoothedPositiveDm =
                    smoothedPositiveDm -
                    smoothedPositiveDm / period +
                    positiveDm[i]

                smoothedNegativeDm =
                    smoothedNegativeDm -
                    smoothedNegativeDm / period +
                    negativeDm[i]
            }

            if (
                !smoothedTr.isFinite() ||
                !smoothedPositiveDm.isFinite() ||
                !smoothedNegativeDm.isFinite()
            ) {
                return null
            }

            if (smoothedTr <= 0.0) {
                dxValues.add(0.0)
                continue
            }

            val plusDi = smoothedPositiveDm / smoothedTr * 100.0
            val minusDi = smoothedNegativeDm / smoothedTr * 100.0
            val denominator = plusDi + minusDi

            val dx = if (denominator > 0.0) {
                abs(plusDi - minusDi) / denominator * 100.0
            } else {
                0.0
            }

            if (!dx.isFinite()) return null

            dxValues.add(dx)
        }

        if (dxValues.size < period) return null

        // Initial ADX: average of the first period DX values.
        var adx = dxValues.take(period).average()

        // Subsequent ADX values: Wilder smoothing.
        for (i in period until dxValues.size) {
            adx = (
                adx * (period - 1) + dxValues[i]
            ) / period
        }

        return adx.takeIf { it.isFinite() }
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

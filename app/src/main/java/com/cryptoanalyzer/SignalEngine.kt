
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
    val reasons: List<String>
)

object SignalEngine {

    fun analyze(candles: List<Candle>): TradingSignal? {

        if (candles.size < 60) return null

        val closes = candles.map { it.close }
        val volumes = candles.map { it.volume }

        val rsi = TechnicalIndicators.rsi(closes)
        val ema20 = TechnicalIndicators.ema(closes, 20)
        val ema50 = TechnicalIndicators.ema(closes, 50)
        val macd = TechnicalIndicators.macd(closes)
        val atr = TechnicalIndicators.atr(candles)
        val bands = TechnicalIndicators.bollingerBands(closes)
        val stochastic = TechnicalIndicators.stochastic(candles)
        val ichimoku = AdvancedIndicators.ichimoku(candles)
        val fibonacci = AdvancedIndicators.fibonacci(candles)

        val last = candles.lastIndex
        val currentPrice = closes[last]
        val currentCandle = candles[last]

        val currentRsi = rsi[last] ?: return null
        val shortEma = ema20[last] ?: return null
        val longEma = ema50[last] ?: return null
        val currentMacd = macd[last] ?: return null
        val currentAtr = atr[last] ?: return null
        val currentBands = bands[last] ?: return null
        val currentStochastic = stochastic[last] ?: return null
        val previousStochastic = stochastic[last - 1]
        val currentIchimoku = ichimoku[last]

        if (currentAtr <= 0.0) return null

        var score = 0
        val reasons = mutableListOf<String>()

        // RSI: maximum 10 points
        when {
            currentRsi in 50.0..70.0 -> {
                score += 10
                reasons.add("RSI مومنتوم صعودی را تأیید می‌کند")
            }
            currentRsi in 30.0..50.0 -> {
                score -= 10
                reasons.add("RSI مومنتوم نزولی را تأیید می‌کند")
            }
        }

        // EMA trend: maximum 12 points
        if (shortEma > longEma) {
            score += 12
            reasons.add("روند EMA20 بالاتر از EMA50 است")
        } else {
            score -= 12
            reasons.add("روند EMA20 پایین‌تر از EMA50 است")
        }

        // Price versus EMA20: maximum 8 points
        if (currentPrice > shortEma) {
            score += 8
            reasons.add("قیمت بالاتر از EMA20 قرار دارد")
        } else {
            score -= 8
            reasons.add("قیمت پایین‌تر از EMA20 قرار دارد")
        }

        // MACD: maximum 12 points
        if (currentMacd.macd > currentMacd.signal) {
            score += 12
            reasons.add("MACD سیگنال صعودی دارد")
        } else {
            score -= 12
            reasons.add("MACD سیگنال نزولی دارد")
        }

        // MACD histogram: maximum 6 points
        if (currentMacd.histogram > 0) {
            score += 6
            reasons.add("هیستوگرام MACD مثبت است")
        } else {
            score -= 6
            reasons.add("هیستوگرام MACD منفی است")
        }

        // Ichimoku: maximum 14 points
        val tenkan = currentIchimoku.tenkan
        val kijun = currentIchimoku.kijun
        val senkouA = currentIchimoku.senkouA
        val senkouB = currentIchimoku.senkouB

        if (tenkan != null && kijun != null) {
            if (tenkan > kijun) {
                score += 7
                reasons.add("خط تنکان بالاتر از کیجون است")
            } else if (tenkan < kijun) {
                score -= 7
                reasons.add("خط تنکان پایین‌تر از کیجون است")
            }
        }

        if (senkouA != null && senkouB != null) {
            val cloudTop = maxOf(senkouA, senkouB)
            val cloudBottom = minOf(senkouA, senkouB)

            when {
                currentPrice > cloudTop -> {
                    score += 7
                    reasons.add("قیمت بالاتر از ابر ایچیموکو است")
                }
                currentPrice < cloudBottom -> {
                    score -= 7
                    reasons.add("قیمت پایین‌تر از ابر ایچیموکو است")
                }
            }
        }

        // Bollinger Bands: maximum 8 points
        when {
            currentPrice > currentBands.upper -> {
                score += 8
                reasons.add("قیمت از باند بالایی بولینگر عبور کرده است")
            }
            currentPrice < currentBands.lower -> {
                score -= 8
                reasons.add("قیمت از باند پایینی بولینگر عبور کرده است")
            }
            currentPrice > currentBands.middle -> {
                score += 4
                reasons.add("قیمت بالاتر از میانگین بولینگر است")
            }
            currentPrice < currentBands.middle -> {
                score -= 4
                reasons.add("قیمت پایین‌تر از میانگین بولینگر است")
            }
        }

        // Stochastic: maximum 8 points
        if (previousStochastic != null) {
            when {
                previousStochastic < 20.0 &&
                    currentStochastic >= 20.0 -> {
                    score += 8
                    reasons.add("استوکاستیک از ناحیه اشباع فروش خارج شده است")
                }

                previousStochastic > 80.0 &&
                    currentStochastic <= 80.0 -> {
                    score -= 8
                    reasons.add("استوکاستیک از ناحیه اشباع خرید خارج شده است")
                }
            }
        }

        // Fibonacci: maximum 6 points
        if (fibonacci != null) {
            val nearestLevel = fibonacci.levels.values.minByOrNull {
                abs(currentPrice - it)
            }

            val tolerance = maxOf(
                currentAtr * 0.35,
                currentPrice * 0.0025
            )

            if (nearestLevel != null &&
                abs(currentPrice - nearestLevel) <= tolerance
            ) {
                if (currentPrice > nearestLevel) {
                    score += 6
                    reasons.add("قیمت نزدیک سطح حمایتی فیبوناچی است")
                } else if (currentPrice < nearestLevel) {
                    score -= 6
                    reasons.add("قیمت نزدیک سطح مقاومتی فیبوناچی است")
                }
            }
        }

        // Volume confirmation: maximum 6 points
        val previousVolumes = volumes.dropLast(1).takeLast(20)

        if (previousVolumes.size == 20) {
            val averageVolume = previousVolumes.average()

            if (averageVolume > 0.0 &&
                currentCandle.volume > averageVolume * 1.2
            ) {
                if (currentCandle.close > currentCandle.open) {
                    score += 6
                    reasons.add("حجم بالاتر از میانگین و کندل صعودی است")
                } else if (currentCandle.close < currentCandle.open) {
                    score -= 6
                    reasons.add("حجم بالاتر از میانگین و کندل نزولی است")
                }
            }
        }

        score = score.coerceIn(-90, 90)

        val type = when {
            score >= 40 -> SignalType.BUY
            score <= -40 -> SignalType.SELL
            else -> SignalType.HOLD
        }

        val stopLoss = when (type) {
            SignalType.BUY -> currentPrice - currentAtr * 1.5
            SignalType.SELL -> currentPrice + currentAtr * 1.5
            SignalType.HOLD -> currentPrice - currentAtr * 1.5
        }

        val takeProfit1 = when (type) {
            SignalType.BUY -> currentPrice + currentAtr * 2.0
            SignalType.SELL -> currentPrice - currentAtr * 2.0
            SignalType.HOLD -> currentPrice + currentAtr * 2.0
        }

        val takeProfit2 = when (type) {
            SignalType.BUY -> currentPrice + currentAtr * 3.0
            SignalType.SELL -> currentPrice - currentAtr * 3.0
            SignalType.HOLD -> currentPrice + currentAtr * 3.0
        }

        return TradingSignal(
            type = type,
            score = score,
            entryPrice = currentPrice,
            stopLoss = stopLoss,
            takeProfit1 = takeProfit1,
            takeProfit2 = takeProfit2,
            reasons = reasons
        )
    }
}

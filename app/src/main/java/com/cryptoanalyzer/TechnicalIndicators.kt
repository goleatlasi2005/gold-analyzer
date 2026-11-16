package com.goldanalyzer

import kotlin.math.abs
import kotlin.math.sqrt

object TechnicalIndicators {

    fun sma(values: List<Double>, period: Int): List<Double?> {
        require(period > 0)
        return values.indices.map { index ->
            if (index + 1 < period) null
            else values.subList(index - period + 1, index + 1).average()
        }
    }

    fun ema(values: List<Double>, period: Int): List<Double?> {
        require(period > 0)
        if (values.isEmpty()) return emptyList()

        val result = MutableList<Double?>(values.size) { null }
        if (values.size < period) return result

        var previous = values.take(period).average()
        result[period - 1] = previous

        val multiplier = 2.0 / (period + 1)

        for (i in period until values.size) {
            previous = (values[i] - previous) * multiplier + previous
            result[i] = previous
        }
        return result
    }

    fun rsi(values: List<Double>, period: Int = 14): List<Double?> {
        require(period > 0)
        val result = MutableList<Double?>(values.size) { null }
        if (values.size <= period) return result

        var gains = 0.0
        var losses = 0.0

        for (i in 1..period) {
            val change = values[i] - values[i - 1]
            if (change > 0) gains += change
            else losses -= change
        }

        var averageGain = gains / period
        var averageLoss = losses / period

        fun calculate(): Double {
            if (averageLoss == 0.0) {
                return if (averageGain == 0.0) 50.0 else 100.0
            }
            val rs = averageGain / averageLoss
            return 100.0 - (100.0 / (1.0 + rs))
        }

        result[period] = calculate()

        for (i in period + 1 until values.size) {
            val change = values[i] - values[i - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0

            averageGain =
                (averageGain * (period - 1) + gain) / period
            averageLoss =
                (averageLoss * (period - 1) + loss) / period

            result[i] = calculate()
        }

        return result
    }

    fun macd(
        values: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): List<MacdPoint?> {
        require(fastPeriod > 0 && slowPeriod > fastPeriod && signalPeriod > 0)

        val fast = ema(values, fastPeriod)
        val slow = ema(values, slowPeriod)

        val line = values.indices.map { index ->
            val f = fast[index]
            val s = slow[index]
            if (f != null && s != null) f - s else null
        }

        val validStart = slowPeriod - 1
        val validLine = line.drop(validStart).filterNotNull()
        val signalValues = ema(validLine, signalPeriod)

        val result = MutableList<MacdPoint?>(values.size) { null }

        for (i in validStart until values.size) {
            val macdValue = line[i] ?: continue
            val signalValue = signalValues[i - validStart] ?: continue

            result[i] = MacdPoint(
                macd = macdValue,
                signal = signalValue,
                histogram = macdValue - signalValue
            )
        }

        return result
    }

    fun bollingerBands(
        values: List<Double>,
        period: Int = 20,
        deviations: Double = 2.0
    ): List<BollingerPoint?> {
        require(period > 0 && deviations >= 0)

        val result = MutableList<BollingerPoint?>(values.size) { null }

        for (i in period - 1 until values.size) {
            val window = values.subList(i - period + 1, i + 1)
            val middle = window.average()
            val variance = window.sumOf {
                (it - middle) * (it - middle)
            } / period
            val standardDeviation = sqrt(variance)

            result[i] = BollingerPoint(
                middle = middle,
                upper = middle + deviations * standardDeviation,
                lower = middle - deviations * standardDeviation
            )
        }

        return result
    }

    fun stochastic(
        candles: List<Candle>,
        period: Int = 14
    ): List<Double?> {
        require(period > 0)

        return candles.indices.map { i ->
            if (i + 1 < period) {
                null
            } else {
                val window = candles.subList(i - period + 1, i + 1)
                val highest = window.maxOf { it.high }
                val lowest = window.minOf { it.low }

                if (highest == lowest) 50.0
                else (candles[i].close - lowest) /
                    (highest - lowest) * 100.0
            }
        }
    }

    fun atr(
        candles: List<Candle>,
        period: Int = 14
    ): List<Double?> {
        require(period > 0)
        if (candles.isEmpty()) return emptyList()

        val trueRanges = candles.indices.map { i ->
            val candle = candles[i]
            if (i == 0) {
                candle.high - candle.low
            } else {
                maxOf(
                    candle.high - candle.low,
                    abs(candle.high - candles[i - 1].close),
                    abs(candle.low - candles[i - 1].close)
                )
            }
        }

        return ema(trueRanges, period)
    }
}

data class MacdPoint(
    val macd: Double,
    val signal: Double,
    val histogram: Double
)

data class BollingerPoint(
    val middle: Double,
    val upper: Double,
    val lower: Double
)

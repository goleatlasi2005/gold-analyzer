
package com.goldanalyzer

import kotlin.math.abs
import kotlin.math.sqrt

object TechnicalIndicators {

    private fun validValues(values: List<Double>): Boolean =
        values.all { it.isFinite() }

    private fun validCandle(candle: Candle): Boolean {
        return candle.openTime > 0L &&
            candle.closeTime > candle.openTime &&
            candle.open.isFinite() &&
            candle.high.isFinite() &&
            candle.low.isFinite() &&
            candle.close.isFinite() &&
            candle.volume.isFinite() &&
            candle.open > 0.0 &&
            candle.high > 0.0 &&
            candle.low > 0.0 &&
            candle.close > 0.0 &&
            candle.volume >= 0.0 &&
            candle.high >= candle.low &&
            candle.high >= maxOf(candle.open, candle.close) &&
            candle.low <= minOf(candle.open, candle.close)
    }

    fun sma(
        values: List<Double>,
        period: Int
    ): List<Double?> {
        require(period > 0)

        val result = MutableList<Double?>(values.size) { null }

        for (index in values.indices) {
            if (index + 1 < period) continue

            val window = values.subList(
                index - period + 1,
                index + 1
            )

            if (!validValues(window)) continue

            val average = window.average()

            if (average.isFinite()) {
                result[index] = average
            }
        }

        return result
    }

    fun ema(
        values: List<Double>,
        period: Int
    ): List<Double?> {
        require(period > 0)

        val result = MutableList<Double?>(values.size) { null }

        if (values.size < period) return result
        if (!validValues(values)) return result

        var previous = values.take(period).average()

        if (!previous.isFinite()) return result

        result[period - 1] = previous

        val multiplier = 2.0 / (period + 1)

        for (i in period until values.size) {
            previous =
                (values[i] - previous) * multiplier + previous

            if (!previous.isFinite()) {
                return MutableList(values.size) { null }
            }

            result[i] = previous
        }

        return result
    }

    fun rsi(
        values: List<Double>,
        period: Int = 14
    ): List<Double?> {
        require(period > 0)

        val result = MutableList<Double?>(values.size) { null }

        if (values.size <= period) return result
        if (!validValues(values)) return result

        var gains = 0.0
        var losses = 0.0

        for (i in 1..period) {
            val change = values[i] - values[i - 1]

            if (!change.isFinite()) return result

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

        val initialRsi = calculate()

        if (!initialRsi.isFinite()) return result

        result[period] = initialRsi

        for (i in period + 1 until values.size) {
            val change = values[i] - values[i - 1]

            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0

            averageGain =
                (averageGain * (period - 1) + gain) / period

            averageLoss =
                (averageLoss * (period - 1) + loss) / period

            val value = calculate()

            if (!value.isFinite()) return result

            result[i] = value
        }

        return result
    }

    fun macd(
        values: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): List<MacdPoint?> {
        require(
            fastPeriod > 0 &&
                slowPeriod > fastPeriod &&
                signalPeriod > 0
        )

        val result = MutableList<MacdPoint?>(values.size) { null }

        if (!validValues(values)) return result

        val fast = ema(values, fastPeriod)
        val slow = ema(values, slowPeriod)

        val line = values.indices.map { index ->
            val f = fast[index]
            val s = slow[index]

            if (f != null && s != null) {
                f - s
            } else {
                null
            }
        }

        val validStart = slowPeriod - 1
        val validLine = line.drop(validStart).filterNotNull()
        val signalValues = ema(validLine, signalPeriod)

        for (i in validStart until values.size) {
            val macdValue = line[i] ?: continue
            val signalValue =
                signalValues[i - validStart] ?: continue

            val histogram = macdValue - signalValue

            if (
                !macdValue.isFinite() ||
                !signalValue.isFinite() ||
                !histogram.isFinite()
            ) {
                continue
            }

            result[i] = MacdPoint(
                macd = macdValue,
                signal = signalValue,
                histogram = histogram
            )
        }

        return result
    }

    fun bollingerBands(
        values: List<Double>,
        period: Int = 20,
        deviations: Double = 2.0
    ): List<BollingerPoint?> {
        require(period > 0)
        require(deviations.isFinite() && deviations >= 0.0)

        val result =
            MutableList<BollingerPoint?>(values.size) { null }

        for (i in period - 1 until values.size) {
            val window = values.subList(
                i - period + 1,
                i + 1
            )

            if (!validValues(window)) continue

            val middle = window.average()

            if (!middle.isFinite()) continue

            val variance = window.sumOf {
                (it - middle) * (it - middle)
            } / period

            if (!variance.isFinite() || variance < 0.0) {
                continue
            }

            val standardDeviation = sqrt(variance)

            val upper = middle + deviations * standardDeviation
            val lower = middle - deviations * standardDeviation

            if (
                !upper.isFinite() ||
                !lower.isFinite()
            ) {
                continue
            }

            result[i] = BollingerPoint(
                middle = middle,
                upper = upper,
                lower = lower
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
                val window = candles.subList(
                    i - period + 1,
                    i + 1
                )

                if (!window.all { validCandle(it) }) {
                    null
                } else {
                    val highest = window.maxOf { it.high }
                    val lowest = window.minOf { it.low }

                    if (
                        !highest.isFinite() ||
                        !lowest.isFinite() ||
                        highest < lowest
                    ) {
                        null
                    } else if (highest == lowest) {
                        50.0
                    } else {
                        val value =
                            (candles[i].close - lowest) /
                                (highest - lowest) * 100.0

                        value.takeIf { it.isFinite() }
                    }
                }
            }
        }
    }

    // ATR با روش استاندارد Wilder
    fun atr(
        candles: List<Candle>,
        period: Int = 14
    ): List<Double?> {
        require(period > 0)

        if (candles.isEmpty()) return emptyList()

        val result = MutableList<Double?>(candles.size) { null }

        if (candles.size < period) return result

        if (!candles.all { validCandle(it) }) {
            return result
        }

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

        if (!validValues(trueRanges)) return result

        // مقدار اولیه: میانگین ساده اولین دوره
        var previousAtr = trueRanges
            .take(period)
            .average()

        if (!previousAtr.isFinite() || previousAtr < 0.0) {
            return result
        }

        result[period - 1] = previousAtr

        // هموارسازی Wilder برای کندل‌های بعدی
        for (i in period until candles.size) {
            previousAtr = (
                previousAtr * (period - 1) + trueRanges[i]
            ) / period

            if (!previousAtr.isFinite() || previousAtr < 0.0) {
                return MutableList(candles.size) { null }
            }

            result[i] = previousAtr
        }

        return result
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

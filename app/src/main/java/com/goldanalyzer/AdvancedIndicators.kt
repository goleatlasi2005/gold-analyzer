
package com.goldanalyzer

data class IchimokuPoint(
    val tenkan: Double?,
    val kijun: Double?,
    val senkouA: Double?,
    val senkouB: Double?,
    val chikou: Double?
)

data class FibonacciLevels(
    val high: Double,
    val low: Double,
    val levels: Map<Double, Double>
)

object AdvancedIndicators {

    private fun isValidCandle(candle: Candle): Boolean {
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

    private fun midpoint(
        candles: List<Candle>,
        endIndex: Int,
        period: Int
    ): Double? {
        if (period <= 0 || endIndex + 1 < period) return null

        val window = candles.subList(
            endIndex - period + 1,
            endIndex + 1
        )

        if (!window.all { isValidCandle(it) }) return null

        val highest = window.maxOf { it.high }
        val lowest = window.minOf { it.low }
        val difference = highest - lowest

        if (!difference.isFinite() || difference < 0.0) return null

        val value = lowest / 2.0 + highest / 2.0

        return value.takeIf { it.isFinite() && it > 0.0 }
    }

    fun ichimoku(
        candles: List<Candle>,
        tenkanPeriod: Int = 9,
        kijunPeriod: Int = 26,
        senkouBPeriod: Int = 52
    ): List<IchimokuPoint> {

        require(tenkanPeriod > 0)
        require(kijunPeriod > 0)
        require(senkouBPeriod > 0)

        return candles.indices.map { index ->

            val tenkan = midpoint(
                candles,
                index,
                tenkanPeriod
            )

            val kijun = midpoint(
                candles,
                index,
                kijunPeriod
            )

            val senkouB = midpoint(
                candles,
                index,
                senkouBPeriod
            )

            val senkouA = if (tenkan != null && kijun != null) {
                val value = tenkan / 2.0 + kijun / 2.0
                value.takeIf { it.isFinite() && it > 0.0 }
            } else {
                null
            }

            val chikou = candles[index].close.takeIf {
                isValidCandle(candles[index]) &&
                    it.isFinite() &&
                    it > 0.0
            }

            IchimokuPoint(
                tenkan = tenkan,
                kijun = kijun,
                senkouA = senkouA,
                senkouB = senkouB,
                chikou = chikou
            )
        }
    }

    fun fibonacci(
        candles: List<Candle>,
        lookback: Int = 100
    ): FibonacciLevels? {

        require(lookback > 0)

        if (candles.size < 2) return null

        val window = candles.takeLast(lookback)

        if (window.size < 2 || !window.all { isValidCandle(it) }) {
            return null
        }

        val high = window.maxOf { it.high }
        val low = window.minOf { it.low }

        val difference = high - low

        if (!difference.isFinite() || difference <= 0.0) {
            return null
        }

        val ratios = listOf(
            0.0,
            0.236,
            0.382,
            0.5,
            0.618,
            0.786,
            1.0
        )

        val levels = ratios.associateWith { ratio ->
            high - difference * ratio
        }

        if (levels.values.any { !it.isFinite() || it <= 0.0 }) {
            return null
        }

        return FibonacciLevels(
            high = high,
            low = low,
            levels = levels
        )
    }

    fun fibonacciExtensions(
        candles: List<Candle>,
        lookback: Int = 100
    ): Map<Double, Double>? {

        val result = fibonacci(candles, lookback)
            ?: return null

        val range = result.high - result.low

        if (!range.isFinite() || range <= 0.0) {
            return null
        }

        val extensions = listOf(
            1.272,
            1.414,
            1.618,
            2.0,
            2.618
        ).associateWith { ratio ->
            result.high + range * (ratio - 1.0)
        }

        if (extensions.values.any { !it.isFinite() || it <= 0.0 }) {
            return null
        }

        return extensions
    }
}

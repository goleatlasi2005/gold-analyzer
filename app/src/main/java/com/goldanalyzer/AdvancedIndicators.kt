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

    private fun midpoint(
        candles: List<Candle>,
        endIndex: Int,
        period: Int
    ): Double? {
        if (endIndex + 1 < period) return null

        val window = candles.subList(
            endIndex - period + 1,
            endIndex + 1
        )

        val highest = window.maxOf { it.high }
        val lowest = window.minOf { it.low }

        return (highest + lowest) / 2.0
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
                candles, index, tenkanPeriod
            )

            val kijun = midpoint(
                candles, index, kijunPeriod
            )

            val senkouB = midpoint(
                candles, index, senkouBPeriod
            )

            val senkouA =
                if (tenkan != null && kijun != null)
                    (tenkan + kijun) / 2.0
                else null

            val chikou = candles[index].close

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

        val high = window.maxOf { it.high }
        val low = window.minOf { it.low }

        val difference = high - low

        if (difference <= 0.0) return null

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

        return listOf(
            1.272,
            1.414,
            1.618,
            2.0,
            2.618
        ).associateWith { ratio ->
            result.high + range * (ratio - 1.0)
        }
    }
}

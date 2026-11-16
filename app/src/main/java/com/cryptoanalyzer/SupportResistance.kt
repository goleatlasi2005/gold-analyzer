
package com.goldanalyzer

import kotlin.math.abs

enum class LevelSide {
    SUPPORT,
    RESISTANCE
}

data class SupportResistanceLevel(
    val price: Double,
    val kind: String,
    val side: LevelSide,
    val touches: Int,
    val strength: Int,
    val distancePercent: Double
)

object SupportResistanceEngine {

    fun analyze(
        candles: List<Candle>
    ): List<SupportResistanceLevel> {

        if (candles.size < 60) return emptyList()

        val currentPrice = candles.last().close
        if (currentPrice <= 0.0) return emptyList()

        val recent = candles.takeLast(200)
        val atr = TechnicalIndicators.atr(recent)
            .lastOrNull() ?: return emptyList()

        val tolerance = maxOf(
            atr * 0.35,
            currentPrice * 0.002
        )

        val rawPivots = mutableListOf<Pair<Double, LevelSide>>()

        for (i in 2 until recent.size - 2) {
            val candle = recent[i]

            val isHigh = (i - 2..i + 2)
                .filter { it != i }
                .all { recent[it].high <= candle.high }

            val isLow = (i - 2..i + 2)
                .filter { it != i }
                .all { recent[it].low >= candle.low }

            if (isHigh) {
                rawPivots.add(candle.high to LevelSide.RESISTANCE)
            }

            if (isLow) {
                rawPivots.add(candle.low to LevelSide.SUPPORT)
            }
        }

        val staticLevels = mutableListOf<SupportResistanceLevel>()

        for (side in LevelSide.values()) {
            val points = rawPivots
                .filter { it.second == side }
                .map { it.first }
                .sorted()

            val clusters = mutableListOf<MutableList<Double>>()

            for (price in points) {
                val cluster = clusters.lastOrNull()

                if (cluster != null &&
                    abs(price - cluster.average()) <= tolerance
                ) {
                    cluster.add(price)
                } else {
                    clusters.add(mutableListOf(price))
                }
            }

            clusters.forEach { cluster ->
                val levelPrice = cluster.average()

                val isRelevant = when (side) {
                    LevelSide.SUPPORT -> levelPrice <= currentPrice
                    LevelSide.RESISTANCE -> levelPrice >= currentPrice
                }

                if (isRelevant) {
                    val touches = cluster.size

                    staticLevels.add(
                        SupportResistanceLevel(
                            price = levelPrice,
                            kind = "استاتیک",
                            side = side,
                            touches = touches,
                            strength = (touches * 25)
                                .coerceIn(25, 100),
                            distancePercent =
                                abs(currentPrice - levelPrice) /
                                    currentPrice * 100.0
                        )
                    )
                }
            }
        }

        val closes = recent.map { it.close }

        val dynamicValues = mutableListOf<Double>()

        TechnicalIndicators.ema(closes, 20)
            .lastOrNull()?.let { dynamicValues.add(it) }

        TechnicalIndicators.ema(closes, 50)
            .lastOrNull()?.let { dynamicValues.add(it) }

        AdvancedIndicators.ichimoku(recent)
            .lastOrNull()?.kijun?.let {
                dynamicValues.add(it)
            }

        val dynamicLevels = dynamicValues
            .distinctBy { (it / tolerance).toLong() }
            .mapNotNull { price ->

                if (price <= 0.0) return@mapNotNull null

                val side = if (price <= currentPrice) {
                    LevelSide.SUPPORT
                } else {
                    LevelSide.RESISTANCE
                }

                SupportResistanceLevel(
                    price = price,
                    kind = "داینامیک",
                    side = side,
                    touches = 0,
                    strength = 50,
                    distancePercent =
                        abs(currentPrice - price) /
                            currentPrice * 100.0
                )
            }

        return (staticLevels + dynamicLevels)
            .sortedBy { it.distancePercent }
            .take(12)
    }
}

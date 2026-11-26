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

        val validCandles = candles
            .asSequence()
            .filter { candle ->
                candle.openTime > 0L &&
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
            .distinctBy { it.openTime }
            .sortedBy { it.openTime }
            .toList()

        if (validCandles.size < 60) return emptyList()

        val currentPrice = validCandles.last().close

        if (!currentPrice.isFinite() || currentPrice <= 0.0) {
            return emptyList()
        }

        val recent = validCandles.takeLast(200)

        val atr = TechnicalIndicators.atr(recent)
            .lastOrNull()
            ?: return emptyList()

        if (!atr.isFinite() || atr <= 0.0) {
            return emptyList()
        }

        val tolerance = maxOf(
            atr * 0.35,
            currentPrice * 0.002
        )

        if (!tolerance.isFinite() || tolerance <= 0.0) {
            return emptyList()
        }

        val rawPivots =
            mutableListOf<Pair<Double, LevelSide>>()

        for (i in 2 until recent.size - 2) {

            val candle = recent[i]

            val isHigh = (i - 2..i + 2)
                .filter { it != i }
                .all { recent[it].high <= candle.high }

            val isLow = (i - 2..i + 2)
                .filter { it != i }
                .all { recent[it].low >= candle.low }

            if (isHigh) {
                rawPivots.add(
                    candle.high to LevelSide.RESISTANCE
                )
            }

            if (isLow) {
                rawPivots.add(
                    candle.low to LevelSide.SUPPORT
                )
            }
        }

        val staticLevels =
            mutableListOf<SupportResistanceLevel>()

        for (side in LevelSide.values()) {

            val points = rawPivots
                .filter { it.second == side }
                .map { it.first }
                .sorted()

            val clusters =
                mutableListOf<MutableList<Double>>()

            for (price in points) {

                val cluster = clusters.lastOrNull()

                if (
                    cluster != null &&
                    abs(price - cluster.average()) <= tolerance
                ) {
                    cluster.add(price)
                } else {
                    clusters.add(mutableListOf(price))
                }
            }

            clusters.forEach { cluster ->

                val levelPrice = cluster.average()

                if (!levelPrice.isFinite() || levelPrice <= 0.0) {
                    return@forEach
                }

                val isRelevant = when (side) {
                    LevelSide.SUPPORT ->
                        levelPrice <= currentPrice

                    LevelSide.RESISTANCE ->
                        levelPrice >= currentPrice
                }

                if (isRelevant) {

                    val touches = cluster.size

                    val distancePercent =
                        abs(currentPrice - levelPrice) /
                            currentPrice * 100.0

                    if (!distancePercent.isFinite()) {
                        return@forEach
                    }

                    staticLevels.add(
                        SupportResistanceLevel(
                            price = levelPrice,
                            kind = "استاتیک",
                            side = side,
                            touches = touches,
                            strength = (touches * 25)
                                .coerceIn(25, 100),
                            distancePercent = distancePercent
                        )
                    )
                }
            }
        }

        val closes = recent.map { it.close }

        val dynamicValues =
            mutableListOf<Double>()

        TechnicalIndicators.ema(closes, 20)
            .lastOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { dynamicValues.add(it) }

        TechnicalIndicators.ema(closes, 50)
            .lastOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { dynamicValues.add(it) }

        AdvancedIndicators.ichimoku(recent)
            .lastOrNull()
            ?.kijun
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { dynamicValues.add(it) }

        val dynamicLevels = dynamicValues
            .distinctBy {
                (it / tolerance).toLong()
            }
            .mapNotNull { price ->

                if (!price.isFinite() || price <= 0.0) {
                    return@mapNotNull null
                }

                val side = if (price <= currentPrice) {
                    LevelSide.SUPPORT
                } else {
                    LevelSide.RESISTANCE
                }

                val distancePercent =
                    abs(currentPrice - price) /
                        currentPrice * 100.0

                if (!distancePercent.isFinite()) {
                    return@mapNotNull null
                }

                SupportResistanceLevel(
                    price = price,
                    kind = "داینامیک",
                    side = side,
                    touches = 0,
                    strength = 50,
                    distancePercent = distancePercent
                )
            }

        return (staticLevels + dynamicLevels)
            .sortedBy { it.distancePercent }
            .take(12)
    }
}

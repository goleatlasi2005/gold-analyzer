package com.goldanalyzer

data class Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closeTime: Long
)

data class CandleSeries(
    val symbol: String,
    val interval: String,
    val candles: List<Candle>
)


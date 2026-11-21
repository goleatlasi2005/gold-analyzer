package com.goldanalyzer

interface MarketCandleRepository {

    suspend fun getCandles(
        intervalMinutes: Int,
        forceRefresh: Boolean = false
    ): CandleSeries

    suspend fun getDailyCandles(
        count: Int = 500,
        forceRefresh: Boolean = false
    ): CandleSeries
}

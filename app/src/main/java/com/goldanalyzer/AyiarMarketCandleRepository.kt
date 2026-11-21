
package com.goldanalyzer

class AyiarMarketCandleRepository(
    private val repository: AyiarRepository
) : MarketCandleRepository {

    override suspend fun getCandles(
        intervalMinutes: Int,
        forceRefresh: Boolean
    ): CandleSeries {
        return repository.getCandles(
            intervalMinutes = intervalMinutes,
            forceRefresh = forceRefresh
        )
    }

    override suspend fun getDailyCandles(
        count: Int,
        forceRefresh: Boolean
    ): CandleSeries {
        return repository.getDailyCandles(
            count = count,
            forceRefresh = forceRefresh
        )
    }
}

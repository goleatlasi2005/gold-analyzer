
package com.goldanalyzer

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class ExchangeAnalysis(
    val exchange: String,
    val symbol: String,
    val timeframe: String,
    val candles: List<Candle>,
    val signal: TradingSignal?
)

class AnalysisRepository(
    private val candleRepository: CandleRepository = CandleRepository()
) {

    suspend fun analyze(
        symbol: String,
        timeframe: String
    ): List<ExchangeAnalysis> = coroutineScope {

        val binanceTask = async {
            runCatching {
                val series = candleRepository.getBinanceCandles(
                    symbol,
                    timeframe,
                    200
                )

                ExchangeAnalysis(
                    exchange = "Binance",
                    symbol = symbol,
                    timeframe = timeframe,
                    candles = series.candles,
                    signal = SignalEngine.analyze(series.candles)
                )
            }
        }

        val nobitexTask = async {
            runCatching {
                val series = candleRepository.getNobitexCandles(
                    symbol,
                    timeframe,
                    200
                )

                ExchangeAnalysis(
                    exchange = "Nobitex",
                    symbol = symbol,
                    timeframe = timeframe,
                    candles = series.candles,
                    signal = SignalEngine.analyze(series.candles)
                )
            }
        }

        listOfNotNull(
            binanceTask.await().getOrNull(),
            nobitexTask.await().getOrNull()
        )
    }
}

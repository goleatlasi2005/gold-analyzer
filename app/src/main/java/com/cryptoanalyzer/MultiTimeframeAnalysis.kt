
package com.goldanalyzer

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs

data class TimeframeSignal(
    val timeframe: String,
    val signal: TradingSignal?
)

data class ExchangeMultiTimeframeAnalysis(
    val exchange: String,
    val symbol: String,
    val timeframeSignals: List<TimeframeSignal>,
    val finalType: SignalType,
    val averageScore: Int,
    val agreementPercent: Int,
    val referenceSignal: TradingSignal?
)

class MultiTimeframeAnalysisRepository(
    private val analysisRepository: AnalysisRepository = AnalysisRepository()
) {
    private val timeframeWeights = linkedMapOf(
        "5m" to 0.5,
        "15m" to 0.75,
        "1h" to 1.0,
        "4h" to 1.5,
        "1d" to 2.0,
        "1w" to 2.5
    )

    private val timeframes = timeframeWeights.keys.toList()

    suspend fun analyze(
        symbol: String
    ): List<ExchangeMultiTimeframeAnalysis> = coroutineScope {

        val tasks = timeframes.map { timeframe ->
            async {
                timeframe to analysisRepository.analyze(symbol, timeframe)
            }
        }

        val resultsByTimeframe = tasks.map { it.await() }

        val exchanges = resultsByTimeframe
            .flatMap { (_, results) -> results.map { it.exchange } }
            .distinct()

        exchanges.mapNotNull { exchange ->

            val frameSignals = resultsByTimeframe.map { (timeframe, results) ->
                val result = results.firstOrNull { it.exchange == exchange }
                TimeframeSignal(timeframe, result?.signal)
            }

            val validSignals = frameSignals.mapNotNull { frame ->
                val signal = frame.signal ?: return@mapNotNull null
                Triple(
                    frame.timeframe,
                    signal,
                    timeframeWeights[frame.timeframe] ?: 0.0
                )
            }.filter { it.third > 0.0 }

            if (validSignals.isEmpty()) {
                return@mapNotNull null
            }

            val totalWeight = validSignals.sumOf { it.third }

            val weightedRawScore = validSignals.sumOf { (_, signal, weight) ->
                signal.score * weight
            } / totalWeight

            val normalizedScore = (weightedRawScore / 90.0 * 100.0)
                .toInt()
                .coerceIn(-100, 100)

            val buyWeight = validSignals
                .filter { it.second.type == SignalType.BUY }
                .sumOf { it.third }

            val sellWeight = validSignals
                .filter { it.second.type == SignalType.SELL }
                .sumOf { it.third }

            val directionalWeight = buyWeight + sellWeight

            val finalType = when {
                normalizedScore >= 45 -> SignalType.BUY
                normalizedScore <= -45 -> SignalType.SELL
                else -> SignalType.HOLD
            }

            val agreementPercent = if (directionalWeight == 0.0) {
                0
            } else {
                (maxOf(buyWeight, sellWeight) * 100.0 / directionalWeight)
                    .toInt()
            }

            // انتخاب قوی‌ترین سیگنال هم‌جهت با نتیجه نهایی
            // با در نظر گرفتن وزن تایم‌فریم
            val referenceSignal = if (finalType == SignalType.HOLD) {
                null
            } else {
                validSignals
                    .filter { it.second.type == finalType }
                    .maxByOrNull { (_, signal, weight) ->
                        abs(signal.score) * weight
                    }?.second
                    ?: validSignals.maxByOrNull { (_, signal, weight) ->
                        abs(signal.score) * weight
                    }?.second
            }

            ExchangeMultiTimeframeAnalysis(
                exchange = exchange,
                symbol = symbol,
                timeframeSignals = frameSignals,
                finalType = finalType,
                averageScore = normalizedScore,
                agreementPercent = agreementPercent,
                referenceSignal = referenceSignal
            )
        }
    }
}

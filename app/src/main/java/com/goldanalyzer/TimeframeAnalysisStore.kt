
package com.goldanalyzer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

data class TimeframeAnalysis(
    val timeframeLabel: String,
    val intervalMinutes: Int?,
    val weightPercent: Int,
    val minimumCandles: Int,
    val longEmaPeriod: Int,
    val series: CandleSeries,
    val completedCandles: List<Candle>,
    val signal: TradingSignal?,
    val loadedAtMillis: Long
)

object TimeframeAnalysisStore {

    private val cache =
        ConcurrentHashMap<String, TimeframeAnalysis>()

    private val locks =
        ConcurrentHashMap<String, Mutex>()

    suspend fun getAnalysis(
        repository: MarketCandleRepository,
        intervalMinutes: Int?,
        nowMillis: Long = System.currentTimeMillis(),
        forceRefresh: Boolean = false
    ): TimeframeAnalysis {

        val timeframeKey = intervalMinutes?.toString() ?: "daily"
        val sourceKey = repository.javaClass.name
        val key = "${sourceKey}_$timeframeKey"

        val mutex = locks.getOrPut(key) { Mutex() }

        return mutex.withLock {

            val cached = cache[key]
            val ttl = cacheTtlMillis(intervalMinutes)

            if (
                !forceRefresh &&
                cached != null &&
                nowMillis - cached.loadedAtMillis < ttl
            ) {
                return@withLock cached
            }

            val series = if (intervalMinutes == null) {
                repository.getDailyCandles(
                    count = 500,
                    forceRefresh = forceRefresh
                )
            } else {
                repository.getCandles(
                    intervalMinutes = intervalMinutes,
                    forceRefresh = forceRefresh
                )
            }

            val config = timeframeConfig(
                intervalMinutes = intervalMinutes,
                repository = repository
            )

            val completedCandles = series.candles
                .filter { it.closeTime <= nowMillis }
                .distinctBy { it.openTime }
                .sortedBy { it.openTime }

            val signal = SignalEngine.analyze(
                candles = completedCandles,
                nowMillis = nowMillis,
                minimumCandles = config.minimumCandles,
                longEmaPeriod = config.longEmaPeriod
            )

            val result = TimeframeAnalysis(
                timeframeLabel = config.label,
                intervalMinutes = intervalMinutes,
                weightPercent = config.weightPercent,
                minimumCandles = config.minimumCandles,
                longEmaPeriod = config.longEmaPeriod,
                series = series,
                completedCandles = completedCandles,
                signal = signal,
                loadedAtMillis = System.currentTimeMillis()
            )

            cache[key] = result

            result
        }
    }

    fun invalidate(intervalMinutes: Int? = null) {
        if (intervalMinutes == null) {
            cache.clear()
        } else {
            val suffix = "_${intervalMinutes}"
            cache.keys.removeAll { it.endsWith(suffix) }

            if (intervalMinutes == 0) {
                cache.keys.removeAll { it.endsWith("_daily") }
            }
        }
    }

    private fun cacheTtlMillis(
        intervalMinutes: Int?
    ): Long {
        return when (intervalMinutes) {
            5 -> 4 * 60_000L
            15 -> 14 * 60_000L
            30 -> 29 * 60_000L
            60 -> 59 * 60_000L
            240 -> 239 * 60_000L
            null -> 12 * 60 * 60_000L
            else -> 60_000L
        }
    }

    private data class TimeframeConfig(
        val label: String,
        val weightPercent: Int,
        val minimumCandles: Int,
        val longEmaPeriod: Int
    )

    private fun timeframeConfig(
        intervalMinutes: Int?,
        repository: MarketCandleRepository
    ): TimeframeConfig {

        return when (intervalMinutes) {
            5 -> TimeframeConfig(
                label = "۵ دقیقه",
                weightPercent = 15,
                minimumCandles = 200,
                longEmaPeriod = 200
            )

            15 -> TimeframeConfig(
                label = "۱۵ دقیقه",
                weightPercent = 30,
                minimumCandles = 200,
                longEmaPeriod = 200
            )

            30 -> {
                val isXauRepository =
                    repository is XauCandleRepository

                TimeframeConfig(
                    label = "۳۰ دقیقه",
                    weightPercent = 55,
                    minimumCandles = if (isXauRepository) 160 else 200,
                    longEmaPeriod = if (isXauRepository) 160 else 200
                )
            }

            60 -> TimeframeConfig(
                label = "۱ ساعت",
                weightPercent = 15,
                minimumCandles = 130,
                longEmaPeriod = 100
            )

            240 -> TimeframeConfig(
                label = "۴ ساعت",
                weightPercent = 30,
                minimumCandles = 130,
                longEmaPeriod = 100
            )

            null -> TimeframeConfig(
                label = "روزانه",
                weightPercent = 55,
                minimumCandles = 200,
                longEmaPeriod = 200
            )

            else -> error("تایم‌فریم معتبر نیست")
        }
    }
}

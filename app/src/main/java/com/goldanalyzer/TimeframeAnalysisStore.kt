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

            if (
                !forceRefresh &&
                cached != null &&
                isCacheFresh(
                    cached = cached,
                    nowMillis = nowMillis
                )
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
                .asSequence()
                .filter { isValidCandle(it) }
                .filter { it.closeTime <= nowMillis }
                .distinctBy { it.openTime }
                .sortedBy { it.openTime }
                .toList()

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
                loadedAtMillis = nowMillis
            )

            cache[key] = result

            result
        }
    }

    private fun isValidCandle(candle: Candle): Boolean {

        val open = candle.open
        val high = candle.high
        val low = candle.low
        val close = candle.close
        val volume = candle.volume

        if (candle.openTime <= 0L) return false
        if (candle.closeTime <= candle.openTime) return false

        if (!open.isFinite()) return false
        if (!high.isFinite()) return false
        if (!low.isFinite()) return false
        if (!close.isFinite()) return false
        if (!volume.isFinite()) return false

        if (open <= 0.0) return false
        if (high <= 0.0) return false
        if (low <= 0.0) return false
        if (close <= 0.0) return false
        if (volume < 0.0) return false

        if (high < low) return false
        if (high < maxOf(open, close)) return false
        if (low > minOf(open, close)) return false

        return true
    }

    private fun isCacheFresh(
        cached: TimeframeAnalysis,
        nowMillis: Long
    ): Boolean {

        if (nowMillis < cached.loadedAtMillis) {
            return false
        }

        val lastCandle = cached.completedCandles.lastOrNull()
            ?: return nowMillis - cached.loadedAtMillis < 60_000L

        val intervalMillis = (
            cached.intervalMinutes?.toLong() ?: 1440L
        ) * 60_000L

        if (intervalMillis <= 0L) {
            return false
        }

        val nextCloseTime = lastCandle.closeTime
            .takeIf {
                it <= Long.MAX_VALUE - intervalMillis
            }
            ?.plus(intervalMillis)
            ?: return false

        return nowMillis < nextCloseTime
    }

    fun invalidate(intervalMinutes: Int? = null) {

        if (intervalMinutes == null) {
            cache.clear()
            return
        }

        val suffix = "_${intervalMinutes}"

        cache.keys.removeAll { it.endsWith(suffix) }

        if (intervalMinutes == 0) {
            cache.keys.removeAll { it.endsWith("_daily") }
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

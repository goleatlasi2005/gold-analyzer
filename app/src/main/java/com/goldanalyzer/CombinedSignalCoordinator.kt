
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate
import java.time.ZoneId

data class CombinedTimeframeResult(
    val timeframeLabel: String,
    val weightPercent: Int,
    val candleCount: Int,
    val lastCandleTime: Long?,
    val signal: TradingSignal?,
    val errorMessage: String? = null
)

data class CombinedPeriodResult(
    val title: String,
    val timeframes: List<CombinedTimeframeResult>,
    val combinedSignal: TradingSignal?
)

data class CombinedSignalSnapshot(
    val shortTerm: CombinedPeriodResult,
    val mediumTerm: CombinedPeriodResult,
    val updatedAt: Long
)

object CombinedSignalCoordinator {

    private val tehranZone = ZoneId.of("Asia/Tehran")

    suspend fun load(
        repository: AyiarRepository,
        nowMillis: Long = System.currentTimeMillis()
    ): CombinedSignalSnapshot {

        val shortTerm = loadShortTerm(repository, nowMillis)
        val mediumTerm = loadMediumTerm(repository, nowMillis)

        return CombinedSignalSnapshot(
            shortTerm = shortTerm,
            mediumTerm = mediumTerm,
            updatedAt = nowMillis
        )
    }

    suspend fun loadShortTerm(
        repository: AyiarRepository,
        nowMillis: Long = System.currentTimeMillis()
    ): CombinedPeriodResult = coroutineScope {

        val today = LocalDate.now(tehranZone)

        val short5Deferred = async(Dispatchers.IO) {
            analyzeTimeframe(
                repository = repository,
                label = "۵ دقیقه",
                weightPercent = 15,
                intervalMinutes = 5,
                nowMillis = nowMillis,
                minimumCandles = 200,
                longEmaPeriod = 200,
                date = today
            )
        }

        val short15Deferred = async(Dispatchers.IO) {
            analyzeTimeframe(
                repository = repository,
                label = "۱۵ دقیقه",
                weightPercent = 30,
                intervalMinutes = 15,
                nowMillis = nowMillis,
                minimumCandles = 200,
                longEmaPeriod = 200,
                date = today
            )
        }

        val short30Deferred = async(Dispatchers.IO) {
            analyzeTimeframe(
                repository = repository,
                label = "۳۰ دقیقه",
                weightPercent = 55,
                intervalMinutes = 30,
                nowMillis = nowMillis,
                minimumCandles = 200,
                longEmaPeriod = 200,
                date = today
            )
        }

        val results = listOf(
            short5Deferred.await(),
            short15Deferred.await(),
            short30Deferred.await()
        )

        CombinedPeriodResult(
            title = "کوتاه‌مدت",
            timeframes = results,
            combinedSignal = combinePeriod(
                results = results,
                primaryTimeframeLabel = "۳۰ دقیقه"
            )
        )
    }

    suspend fun loadMediumTerm(
        repository: AyiarRepository,
        nowMillis: Long = System.currentTimeMillis()
    ): CombinedPeriodResult = coroutineScope {

        val today = LocalDate.now(tehranZone)

        // دریافت سه تایم‌فریم میان‌مدت به‌صورت هم‌زمان
        val medium1hDeferred = async(Dispatchers.IO) {
            analyzeTimeframe(
                repository = repository,
                label = "۱ ساعت",
                weightPercent = 15,
                intervalMinutes = 60,
                nowMillis = nowMillis,
                minimumCandles = 130,
                longEmaPeriod = 100,
                date = today
            )
        }

        val medium4hDeferred = async(Dispatchers.IO) {
            analyzeTimeframe(
                repository = repository,
                label = "۴ ساعت",
                weightPercent = 30,
                intervalMinutes = 240,
                nowMillis = nowMillis,
                minimumCandles = 130,
                longEmaPeriod = 100,
                date = today
            )
        }

        val mediumDailyDeferred = async(Dispatchers.IO) {
            analyzeTimeframe(
                repository = repository,
                label = "روزانه",
                weightPercent = 55,
                intervalMinutes = null,
                nowMillis = nowMillis,
                minimumCandles = 200,
                longEmaPeriod = 200,
                date = today
            )
        }

        val results = listOf(
            medium1hDeferred.await(),
            medium4hDeferred.await(),
            mediumDailyDeferred.await()
        )

        CombinedPeriodResult(
            title = "میان‌مدت",
            timeframes = results,
            combinedSignal = combinePeriod(
                results = results,
                primaryTimeframeLabel = "روزانه"
            )
        )
    }

    private suspend fun analyzeTimeframe(
        repository: AyiarRepository,
        label: String,
        weightPercent: Int,
        intervalMinutes: Int?,
        nowMillis: Long,
        minimumCandles: Int,
        longEmaPeriod: Int,
        date: LocalDate
    ): CombinedTimeframeResult {

        return try {
            val series = if (intervalMinutes == null) {
                repository.getDailyCandles(500)
            } else {
                repository.getCandles(
                    date = date,
                    intervalMinutes = intervalMinutes
                )
            }

            val completedCandles = series.candles
                .filter { it.closeTime <= nowMillis }
                .sortedBy { it.openTime }

            val signal = SignalEngine.analyze(
                candles = completedCandles,
                nowMillis = nowMillis,
                minimumCandles = minimumCandles,
                longEmaPeriod = longEmaPeriod
            )

            CombinedTimeframeResult(
                timeframeLabel = label,
                weightPercent = weightPercent,
                candleCount = completedCandles.size,
                lastCandleTime = completedCandles.lastOrNull()?.closeTime,
                signal = signal,
                errorMessage = if (signal == null) {
                    "داده کامل برای محاسبه سیگنال کافی نیست."
                } else {
                    null
                }
            )
        } catch (e: Exception) {
            CombinedTimeframeResult(
                timeframeLabel = label,
                weightPercent = weightPercent,
                candleCount = 0,
                lastCandleTime = null,
                signal = null,
                errorMessage = e.message
                    ?: "خطا در دریافت یا تحلیل کندل‌ها"
            )
        }
    }

    private fun combinePeriod(
        results: List<CombinedTimeframeResult>,
        primaryTimeframeLabel: String
    ): TradingSignal? {

        // هر سه تایم‌فریم باید سیگنال معتبر داشته باشند.
        if (results.size != 3 || results.any { it.signal == null }) {
            return null
        }

        val weightedSignals = results.map { result ->
            WeightedTradingSignal(
                timeframeLabel = result.timeframeLabel,
                weightPercent = result.weightPercent,
                signal = result.signal!!
            )
        }

        return CombinedSignalEngine.combine(
            signals = weightedSignals,
            primaryTimeframeLabel = primaryTimeframeLabel
        )
    }
}


package com.goldanalyzer

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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

        val fiveMinute = async {
            loadTimeframe(
                repository = repository,
                intervalMinutes = 5,
                label = "۵ دقیقه",
                weightPercent = 15,
                nowMillis = nowMillis
            )
        }

        val fifteenMinute = async {
            loadTimeframe(
                repository = repository,
                intervalMinutes = 15,
                label = "۱۵ دقیقه",
                weightPercent = 30,
                nowMillis = nowMillis
            )
        }

        val thirtyMinute = async {
            loadTimeframe(
                repository = repository,
                intervalMinutes = 30,
                label = "۳۰ دقیقه",
                weightPercent = 55,
                nowMillis = nowMillis
            )
        }

        val results = listOf(
            fiveMinute.await(),
            fifteenMinute.await(),
            thirtyMinute.await()
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
    ): CombinedPeriodResult {

        // ابتدا ۴ ساعته دریافت می‌شود.
        val fourHour = loadTimeframe(
            repository = repository,
            intervalMinutes = 240,
            label = "۴ ساعت",
            weightPercent = 30,
            nowMillis = nowMillis
        )

        // سپس داده روزانه دریافت می‌شود.
        val daily = loadTimeframe(
            repository = repository,
            intervalMinutes = null,
            label = "روزانه",
            weightPercent = 55,
            nowMillis = nowMillis
        )

        // یک‌ساعته در آخر دریافت می‌شود تا فرصت داشته باشد
        // از تحلیل اختصاصی و داده ذخیره‌شده استفاده کند.
        val oneHour = loadTimeframe(
            repository = repository,
            intervalMinutes = 60,
            label = "۱ ساعت",
            weightPercent = 15,
            nowMillis = nowMillis
        )

        // ترتیب خروجی و وزن‌ها مطابق ساختار اصلی حفظ می‌شود.
        val results = listOf(
            oneHour,
            fourHour,
            daily
        )

        return CombinedPeriodResult(
            title = "میان‌مدت",
            timeframes = results,
            combinedSignal = combinePeriod(
                results = results,
                primaryTimeframeLabel = "روزانه"
            )
        )
    }

    private suspend fun loadTimeframe(
        repository: AyiarRepository,
        intervalMinutes: Int?,
        label: String,
        weightPercent: Int,
        nowMillis: Long
    ): CombinedTimeframeResult {

        return try {
            // استفاده از همان تحلیل و داده ذخیره‌شده
            // که بخش سیگنال اختصاصی نیز از آن استفاده می‌کند.
            val analysis = TimeframeAnalysisStore.getAnalysis(
                repository = repository,
                intervalMinutes = intervalMinutes,
                nowMillis = nowMillis
            )

            CombinedTimeframeResult(
                timeframeLabel = label,
                weightPercent = weightPercent,
                candleCount = analysis.completedCandles.size,
                lastCandleTime = analysis.completedCandles
                    .lastOrNull()
                    ?.closeTime,
                signal = analysis.signal,
                errorMessage = if (analysis.signal == null) {
                    "داده کامل برای محاسبه سیگنال کافی نیست."
                } else {
                    null
                }
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
        if (
            results.size != 3 ||
            results.any { it.signal == null }
        ) {
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

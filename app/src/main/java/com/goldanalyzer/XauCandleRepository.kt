
package com.goldanalyzer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class XauCandleRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
) : MarketCandleRepository {

    override suspend fun getCandles(
        intervalMinutes: Int,
        forceRefresh: Boolean
    ): CandleSeries = withContext(Dispatchers.IO) {

        require(intervalMinutes in listOf(5, 15, 30, 60, 240, 0)) {
            "تایم‌فریم انس معتبر نیست"
        }

        val sourceInterval = when (intervalMinutes) {
            240 -> "60m"
            0 -> "1d"
            else -> "${intervalMinutes}m"
        }

        val range = when (intervalMinutes) {
            5, 15 -> "5d"
            30, 60 -> "1mo"
            240 -> "3mo"
            else -> "1y"
        }

        val request = Request.Builder()
            .url(
                "https://xaus.com/api/v1/chart" +
                    "?symbol=xau" +
                    "&range=$range" +
                    "&interval=$sourceInterval"
            )
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        var response: Response? = null
        var lastNetworkError: IOException? = null

        for (attempt in 0..2) {

            currentCoroutineContext().ensureActive()

            try {
                response = httpClient.newCall(request).execute()

                currentCoroutineContext().ensureActive()

                lastNetworkError = null
                break

            } catch (e: CancellationException) {
                throw e

            } catch (e: IOException) {

                currentCoroutineContext().ensureActive()

                lastNetworkError = e

                if (attempt < 2) {
                    delay(700L * (attempt + 1))
                }
            }
        }

        currentCoroutineContext().ensureActive()

        val receivedResponse = response
            ?: throw (
                lastNetworkError
                    ?: IOException("ارتباط با سرور انس برقرار نشد")
                )

        receivedResponse.use { response ->

            currentCoroutineContext().ensureActive()

            if (!response.isSuccessful) {
                error("خطای دریافت کندل انس: ${response.code}")
            }

            val body = response.body?.string()
                ?: error("پاسخی برای نمودار انس دریافت نشد")

            currentCoroutineContext().ensureActive()

            val root = JSONObject(body)

            val points = root.optJSONArray("points")
                ?: error("اطلاعات کندل انس در پاسخ وجود ندارد")

            val durationMinutes = when (intervalMinutes) {
                0 -> 1440
                240 -> 60
                else -> intervalMinutes
            }

            val durationMillis = durationMinutes * 60_000L
            val candles = mutableListOf<Candle>()

            for (i in 0 until points.length()) {

                if (i % 100 == 0) {
                    currentCoroutineContext().ensureActive()
                }

                val item = points.optJSONObject(i) ?: continue

                val timestamp = item.optLong("t", 0L)
                val open = item.optDouble("o", Double.NaN)
                val high = item.optDouble("h", Double.NaN)
                val low = item.optDouble("l", Double.NaN)
                val close = item.optDouble("c", Double.NaN)
                val volume = item.optDouble("v", 0.0)

                if (
                    timestamp <= 0L ||
                    !open.isFinite() ||
                    !high.isFinite() ||
                    !low.isFinite() ||
                    !close.isFinite() ||
                    !volume.isFinite() ||
                    open <= 0.0 ||
                    high <= 0.0 ||
                    low <= 0.0 ||
                    close <= 0.0 ||
                    volume < 0.0 ||
                    high < low ||
                    high < maxOf(open, close) ||
                    low > minOf(open, close)
                ) {
                    continue
                }

                val openTime = if (timestamp < 10_000_000_000L) {
                    if (timestamp > Long.MAX_VALUE / 1000L) {
                        continue
                    }
                    timestamp * 1000L
                } else {
                    timestamp
                }

                if (
                    openTime <= 0L ||
                    openTime > Long.MAX_VALUE - durationMillis
                ) {
                    continue
                }

                val candle = Candle(
                    openTime = openTime,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume,
                    closeTime = openTime + durationMillis - 1L
                )

                if (isValidCandle(candle)) {
                    candles.add(candle)
                }
            }

            currentCoroutineContext().ensureActive()

            val sorted = candles
                .distinctBy { it.openTime }
                .sortedBy { it.openTime }

            if (sorted.isEmpty()) {
                error("هیچ کندل معتبری برای انس دریافت نشد")
            }

            val result = if (intervalMinutes == 240) {

                sorted
                    .groupBy { it.openTime / 14_400_000L }
                    .values
                    .mapNotNull { group ->

                        val ordered = group.sortedBy { it.openTime }

                        if (ordered.size != 4) {
                            return@mapNotNull null
                        }

                        val first = ordered.first()
                        val last = ordered.last()

                        val isComplete = ordered.zipWithNext()
                            .all { (a, b) ->
                                b.openTime - a.openTime == 3_600_000L
                            }

                        if (!isComplete) {
                            return@mapNotNull null
                        }

                        val bucketStart =
                            (first.openTime / 14_400_000L) *
                                14_400_000L

                        val aggregated = Candle(
                            openTime = bucketStart,
                            open = first.open,
                            high = ordered.maxOf { it.high },
                            low = ordered.minOf { it.low },
                            close = last.close,
                            volume = ordered.sumOf { it.volume },
                            closeTime = bucketStart +
                                14_400_000L - 1L
                        )

                        aggregated.takeIf { isValidCandle(it) }
                    }

            } else {
                sorted
            }

            currentCoroutineContext().ensureActive()

            if (result.isEmpty()) {
                error("کندل کامل برای تایم‌فریم انتخاب‌شده دریافت نشد")
            }

            CandleSeries(
                symbol = "XAUUSD",
                interval = if (intervalMinutes == 0) {
                    "1d"
                } else {
                    "${intervalMinutes}m"
                },
                candles = result
            )
        }
    }

    override suspend fun getDailyCandles(
        count: Int,
        forceRefresh: Boolean
    ): CandleSeries {

        require(count > 0) {
            "تعداد کندل باید بیشتر از صفر باشد"
        }

        val dailySeries = getCandles(
            intervalMinutes = 0,
            forceRefresh = forceRefresh
        )

        return dailySeries.copy(
            candles = dailySeries.candles.takeLast(count)
        )
    }

    private fun isValidCandle(candle: Candle): Boolean {
        return candle.openTime > 0L &&
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
}

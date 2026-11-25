
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.delay
import java.io.IOException
import okhttp3.Response

class XauCandleRepository(
    private val httpClient: OkHttpClient = OkHttpClient()
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

        httpClient.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                error("خطای دریافت کندل انس: ${response.code}")
            }

            val body = response.body?.string()
                ?: error("پاسخی برای نمودار انس دریافت نشد")

            val root = JSONObject(body)

            val points = root.optJSONArray("points")
                ?: error("اطلاعات کندل انس در پاسخ وجود ندارد")

            val candles = mutableListOf<Candle>()

            for (i in 0 until points.length()) {
                val item = points.optJSONObject(i) ?: continue

                val timestamp = item.optLong("t", 0L)
                val open = item.optDouble("o", 0.0)
                val high = item.optDouble("h", 0.0)
                val low = item.optDouble("l", 0.0)
                val close = item.optDouble("c", 0.0)
                val volume = item.optDouble("v", 0.0)

                if (
                    timestamp <= 0L ||
                    open <= 0.0 ||
                    high <= 0.0 ||
                    low <= 0.0 ||
                    close <= 0.0 ||
                    high < low ||
                    high < maxOf(open, close) ||
                    low > minOf(open, close)
                ) {
                    continue
                }

                val openTime = if (timestamp < 10_000_000_000L) {
                    timestamp * 1000L
                } else {
                    timestamp
                }

                val durationMinutes = when (intervalMinutes) {
                    0 -> 1440
                    240 -> 60
                    else -> intervalMinutes
                }

                candles.add(
                    Candle(
                        openTime = openTime,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume.coerceAtLeast(0.0),
                        closeTime = openTime +
                            durationMinutes * 60_000L - 1L
                    )
                )
            }

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

                        // فقط چهار کندل ساعتی کامل و پیوسته
                        // برای ساخت کندل چهارساعته پذیرفته می‌شود.
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

                        Candle(
                            openTime = bucketStart,
                            open = first.open,
                            high = ordered.maxOf { it.high },
                            low = ordered.minOf { it.low },
                            close = last.close,
                            volume = ordered.sumOf { it.volume },
                            closeTime = bucketStart +
                                14_400_000L - 1L
                        )
                    }

            } else {
                sorted
            }

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
}

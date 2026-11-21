
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class XauCandleRepository(
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    suspend fun getCandles(intervalMinutes: Int): CandleSeries =
        withContext(Dispatchers.IO) {

            require(intervalMinutes in listOf(5, 15, 30, 60, 240, 0)) {
                "تایم‌فریم انس معتبر نیست"
            }

            val sourceInterval = if (intervalMinutes == 240) {
                "60m"
            } else if (intervalMinutes == 0) {
                "1d"
            } else {
                "${intervalMinutes}m"
            }

            val range = when (intervalMinutes) {
                5, 15, 30 -> "5d"
                60 -> "1mo"
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
                        high < low
                    ) {
                        continue
                    }

                    val openTime = if (timestamp < 10_000_000_000L) {
                        timestamp * 1000L
                    } else {
                        timestamp
                    }

                    val duration = if (intervalMinutes == 0) {
                        1440
                    } else {
                        if (intervalMinutes == 240) 60 else intervalMinutes
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
                                duration * 60_000L - 1L
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
                    sorted.groupBy {
                        it.openTime / 14_400_000L
                    }.values.map { group ->
                        val ordered = group.sortedBy { it.openTime }
                        val first = ordered.first()
                        val last = ordered.last()

                        Candle(
                            openTime = first.openTime,
                            open = first.open,
                            high = ordered.maxOf { it.high },
                            low = ordered.minOf { it.low },
                            close = last.close,
                            volume = ordered.sumOf { it.volume },
                            closeTime = last.closeTime
                        )
                    }
                } else {
                    sorted
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
}

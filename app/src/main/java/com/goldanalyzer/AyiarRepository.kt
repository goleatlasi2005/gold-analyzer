
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class AyiarRepository(
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    companion object {
        const val INSTRUMENT_CODE = "34144395039913458"

        private const val BASE_URL =
            "https://cdn.tsetmc.com/api/Trade/GetTradeHistory"

        private val TEHRAN_ZONE = ZoneId.of("Asia/Tehran")
    }

    private data class Trade(
        val timeMillis: Long,
        val tradeNumber: Long,
        val price: Double,
        val volume: Double
    )

    suspend fun getCandles(
        date: LocalDate = LocalDate.now(TEHRAN_ZONE),
        intervalMinutes: Int = 5
    ): CandleSeries = withContext(Dispatchers.IO) {

        require(intervalMinutes in listOf(5, 15, 30, 60, 240)) {
            "تایم‌فریم انتخاب‌شده معتبر نیست"
        }

        val dateText = date.toString().replace("-", "")

        val url = "$BASE_URL/$INSTRUMENT_CODE/$dateText/false"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                error("خطای دریافت معاملات عیار: ${response.code}")
            }

            val body = response.body?.string()
                ?: error("پاسخی از TSETMC دریافت نشد")

            val root = JSONObject(body)

            val history = root.optJSONArray("tradeHistory")
                ?: error("فهرست معاملات در پاسخ وجود ندارد")

            if (history.length() == 0) {
                error("برای این تاریخ معامله‌ای دریافت نشد")
            }

            val trades = mutableListOf<Trade>()

            for (i in 0 until history.length()) {

                val item = history.optJSONObject(i) ?: continue

                if (item.optInt("canceled", 0) != 0) {
                    continue
                }

                val price = item.optDouble("pTran", 0.0)
                val volume = item.optDouble("qTitTran", 0.0)
                val timeValue = item.optInt("hEven", 0)

                if (price <= 0.0 || volume <= 0.0 || timeValue <= 0) {
                    continue
                }

                val hour = timeValue / 10000
                val minute = (timeValue / 100) % 100
                val second = timeValue % 100

                if (
                    hour !in 0..23 ||
                    minute !in 0..59 ||
                    second !in 0..59
                ) {
                    continue
                }

                val localTime = LocalTime.of(
                    hour,
                    minute,
                    second
                )

                val timestamp = date
                    .atTime(localTime)
                    .atZone(TEHRAN_ZONE)
                    .toInstant()
                    .toEpochMilli()

                trades.add(
                    Trade(
                        timeMillis = timestamp,
                        tradeNumber = item.optLong("nTran", 0L),
                        price = price,
                        volume = volume
                    )
                )
            }

            if (trades.isEmpty()) {
                error("معامله معتبر برای ساخت کندل پیدا نشد")
            }

            val sortedTrades = trades.sortedWith(
                compareBy<Trade> { it.timeMillis }
                    .thenBy { it.tradeNumber }
            )

            // تعیین مرز کندل بر اساس ساعت محلی تهران
            val grouped = sortedTrades.groupBy { trade ->

                val localDateTime = java.time.Instant
                    .ofEpochMilli(trade.timeMillis)
                    .atZone(TEHRAN_ZONE)
                    .toLocalDateTime()

                val minuteOfDay =
                    localDateTime.hour * 60 +
                    localDateTime.minute

                val bucketMinute =
                    (minuteOfDay / intervalMinutes) *
                    intervalMinutes

                date.atStartOfDay()
                    .plusMinutes(bucketMinute.toLong())
                    .atZone(TEHRAN_ZONE)
                    .toInstant()
                    .toEpochMilli()
            }

            val intervalMillis =
                intervalMinutes * 60_000L

            val candles = grouped
                .toSortedMap()
                .map { (bucketStart, bucketTrades) ->

                    val prices = bucketTrades.map { it.price }

                    Candle(
                        openTime = bucketStart,
                        open = bucketTrades.first().price,
                        high = prices.maxOrNull()!!,
                        low = prices.minOrNull()!!,
                        close = bucketTrades.last().price,
                        volume = bucketTrades.sumOf { it.volume },
                        closeTime = bucketStart +
                            intervalMillis - 1L
                    )
                }

            if (candles.isEmpty()) {
                error("کندلی برای این بازه ساخته نشد")
            }

            CandleSeries(
                symbol = "عیار",
                interval = "${intervalMinutes}m",
                candles = candles
            )
        }
    }
}

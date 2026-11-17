
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AyiarQuote(
    val currentPrice: Double,
    val closingPrice: Double,
    val previousClose: Double,
    val date: Long,
    val time: Long
)

class AyiarRepository(
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    companion object {
        const val INSTRUMENT_CODE = "34144395039913458"

        private const val BASE_URL =
            "https://cdn.tsetmc.com/api/Trade/GetTradeHistory"

        private const val DAILY_URL =
            "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceDailyList"

        private const val QUOTE_URL =
            "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo"

        private const val TARGET_INTRADAY_CANDLES = 300
        private const val MAX_LOOKBACK_DAYS = 450

        private val TEHRAN_ZONE = ZoneId.of("Asia/Tehran")
        private val DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE
    }

    private data class Trade(
        val timeMillis: Long,
        val tradeNumber: Long,
        val price: Double,
        val volume: Double
    )

    private val intradayCache =
        mutableMapOf<Int, MutableMap<LocalDate, List<Candle>>>()

    // دریافت قیمت مستقل از نمودار و کندل‌ها
    suspend fun getQuote(): AyiarQuote = withContext(Dispatchers.IO) {

        val url = "$QUOTE_URL/$INSTRUMENT_CODE"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                error("خطای دریافت قیمت عیار: ${response.code}")
            }

            val body = response.body?.string()
                ?: error("پاسخی برای قیمت عیار دریافت نشد")

            val root = JSONObject(body)

            val info = root.optJSONObject("closingPriceInfo") ?: root

            val lastPrice = info.optDouble("pDrCotVal", 0.0)
            val closingPrice = info.optDouble("pClosing", 0.0)
            val previousClose = info.optDouble("priceYesterday", 0.0)

            if (lastPrice <= 0.0 && closingPrice <= 0.0) {
                error("قیمت معتبر عیار در پاسخ TSETMC وجود ندارد")
            }

            val date = info.optLong("dEven", 0L)
            val time = info.optLong("hEven", 0L)

            AyiarQuote(
                currentPrice = if (lastPrice > 0.0) lastPrice else closingPrice,
                closingPrice = closingPrice,
                previousClose = previousClose,
                date = date,
                time = time
            )
        }
    }

    suspend fun getCandles(
        date: LocalDate = LocalDate.now(TEHRAN_ZONE),
        intervalMinutes: Int = 5
    ): CandleSeries = withContext(Dispatchers.IO) {

        require(intervalMinutes in listOf(5, 15, 30, 60, 240)) {
            "تایم‌فریم انتخاب‌شده معتبر نیست"
        }

        val cache = synchronized(intradayCache) {
            intradayCache.getOrPut(intervalMinutes) {
                mutableMapOf()
            }
        }

        val allCandles = mutableListOf<Candle>()

        for (daysBack in 0..MAX_LOOKBACK_DAYS) {
            if (allCandles.size >= TARGET_INTRADAY_CANDLES) {
                break
            }

            val targetDate = date.minusDays(daysBack.toLong())

            if (
                targetDate.dayOfWeek.value == 5 ||
                targetDate.dayOfWeek.value == 6
            ) {
                continue
            }

            val dailyCandles = if (daysBack == 0) {
                val fresh = fetchCandlesForDate(
                    date = targetDate,
                    intervalMinutes = intervalMinutes
                )

                synchronized(cache) {
                    cache[targetDate] = fresh?.candles ?: emptyList()
                }

                fresh?.candles ?: emptyList()
            } else {
                val cached = synchronized(cache) {
                    cache[targetDate]
                }

                if (cached != null) {
                    cached
                } else {
                    val fetched = fetchCandlesForDate(
                        date = targetDate,
                        intervalMinutes = intervalMinutes
                    )

                    val result = fetched?.candles ?: emptyList()

                    synchronized(cache) {
                        cache[targetDate] = result
                    }

                    result
                }
            }

            allCandles.addAll(dailyCandles)
        }

        val sortedCandles = allCandles
            .distinctBy { it.openTime }
            .sortedBy { it.openTime }
            .takeLast(TARGET_INTRADAY_CANDLES)

        if (sortedCandles.isEmpty()) {
            error("هیچ کندل معتبری برای صندوق عیار دریافت نشد")
        }

        CandleSeries(
            symbol = "عیار",
            interval = "${intervalMinutes}m",
            candles = sortedCandles
        )
    }

    suspend fun getDailyCandles(
        count: Int = 500
    ): CandleSeries = withContext(Dispatchers.IO) {

        require(count in 1..5000) {
            "تعداد کندل‌های درخواستی معتبر نیست"
        }

        val url = "$DAILY_URL/$INSTRUMENT_CODE/$count"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                error("خطای دریافت تاریخچه روزانه عیار: ${response.code}")
            }

            val body = response.body?.string()
                ?: error("پاسخی از تاریخچه روزانه دریافت نشد")

            val root = JSONObject(body)

            val history = root.optJSONArray("closingPriceDaily")
                ?: error("تاریخچه روزانه در پاسخ TSETMC وجود ندارد")

            val candles = mutableListOf<Candle>()

            for (i in 0 until history.length()) {
                val item = history.optJSONObject(i) ?: continue

                val dateValue = item.optLong("dEven", 0L)
                if (dateValue <= 0L) continue

                val dateText = dateValue.toString().padStart(8, '0')

                val date = try {
                    LocalDate.parse(dateText, DATE_FORMAT)
                } catch (_: Exception) {
                    continue
                }

                val open = item.optDouble("priceFirst", 0.0)
                val high = item.optDouble("priceMax", 0.0)
                val low = item.optDouble("priceMin", 0.0)
                val close = item.optDouble("pClosing", 0.0)
                val volume = item.optDouble("qTotTran5J", 0.0)

                if (
                    open <= 0.0 ||
                    high <= 0.0 ||
                    low <= 0.0 ||
                    close <= 0.0 ||
                    high < low
                ) {
                    continue
                }

                val openTime = date
                    .atStartOfDay(TEHRAN_ZONE)
                    .toInstant()
                    .toEpochMilli()

                candles.add(
                    Candle(
                        openTime = openTime,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume.coerceAtLeast(0.0),
                        closeTime = openTime + 86_400_000L - 1L
                    )
                )
            }

            if (candles.isEmpty()) {
                error("هیچ کندل روزانه معتبری برای عیار دریافت نشد")
            }

            CandleSeries(
                symbol = "عیار",
                interval = "1d",
                candles = candles.sortedBy { it.openTime }
            )
        }
    }

    private fun fetchCandlesForDate(
        date: LocalDate,
        intervalMinutes: Int
    ): CandleSeries? {

        val dateText = date.toString().replace("-", "")
        val url = "$BASE_URL/$INSTRUMENT_CODE/$dateText/false"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                error("خطای دریافت معاملات عیار: ${response.code}")
            }

            val body = response.body?.string()
                ?: error("پاسخی از TSETMC دریافت نشد")

            val root = JSONObject(body)

            val history = root.optJSONArray("tradeHistory")
                ?: error("فهرست معاملات در پاسخ وجود ندارد")

            if (history.length() == 0) {
                return@use null
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

                val localTime = LocalTime.of(hour, minute, second)

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
                return@use null
            }

            val sortedTrades = trades.sortedWith(
                compareBy<Trade> { it.timeMillis }
                    .thenBy { it.tradeNumber }
            )

            val grouped = sortedTrades.groupBy { trade ->
                val localDateTime = Instant
                    .ofEpochMilli(trade.timeMillis)
                    .atZone(TEHRAN_ZONE)
                    .toLocalDateTime()

                val minuteOfDay =
                    localDateTime.hour * 60 + localDateTime.minute

                val bucketMinute =
                    (minuteOfDay / intervalMinutes) * intervalMinutes

                date.atStartOfDay()
                    .plusMinutes(bucketMinute.toLong())
                    .atZone(TEHRAN_ZONE)
                    .toInstant()
                    .toEpochMilli()
            }

            val intervalMillis = intervalMinutes * 60_000L

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
                        closeTime = bucketStart + intervalMillis - 1L
                    )
                }

            if (candles.isEmpty()) {
                return@use null
            }

            CandleSeries(
                symbol = "عیار",
                interval = "${intervalMinutes}m",
                candles = candles
            )
        }
    }
}

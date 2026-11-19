
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class AyiarQuote(
    val currentPrice: Double,
    val closingPrice: Double,
    val previousClose: Double,
    val date: Long,
    val time: Long
)

class AyiarRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {

    companion object {
        const val INSTRUMENT_CODE = "34144395039913458"

        private const val WORKER_URL =
            "https://mofid-candles.gole-atlasi2005.workers.dev/history"

        private const val DAILY_URL =
            "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceDailyList"

        private const val QUOTE_URL =
            "https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo"

        private const val TARGET_INTRADAY_CANDLES = 300

        private val TEHRAN_ZONE = ZoneId.of("Asia/Tehran")
        private val DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE

        private fun historyDays(intervalMinutes: Int): Int {
            return when (intervalMinutes) {
                5 -> 7
                15 -> 21
                30 -> 45
                60 -> 60
                240 -> 240
                else -> 30
            }
        }

        private fun cacheDuration(intervalMinutes: Int): Long {
            return when (intervalMinutes) {
                5 -> 4 * 60_000L
                15 -> 14 * 60_000L
                30 -> 29 * 60_000L
                60 -> 59 * 60_000L
                240 -> 239 * 60_000L
                else -> 60_000L
            }
        }
    }

    private data class CachedSeries(
        val series: CandleSeries,
        val savedAtMillis: Long
    )

    // حافظه مشترک کندل‌ها برای سیگنال اختصاصی و ترکیبی
    private val candleCache =
        ConcurrentHashMap<String, CachedSeries>()

    // جلوگیری از درخواست هم‌زمان تکراری برای یک تایم‌فریم
    private val candleLocks =
        ConcurrentHashMap<String, Mutex>()

    private fun getCachedSeries(
        key: String,
        durationMillis: Long
    ): CandleSeries? {
        val cached = candleCache[key] ?: return null
        val age = System.currentTimeMillis() - cached.savedAtMillis

        return if (age >= 0 && age < durationMillis) {
            cached.series
        } else {
            null
        }
    }

    // دریافت قیمت لحظه‌ای از TSETMC
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

            AyiarQuote(
                currentPrice = if (lastPrice > 0.0) lastPrice else closingPrice,
                closingPrice = closingPrice,
                previousClose = previousClose,
                date = info.optLong("dEven", 0L),
                time = info.optLong("hEven", 0L)
            )
        }
    }

    // دریافت کندل‌های صندوق عیار از بورس‌ویو مفید
    // سیگنال اختصاصی و ترکیبی از همین داده مشترک استفاده می‌کنند.
    suspend fun getCandles(
        date: LocalDate = LocalDate.now(TEHRAN_ZONE),
        intervalMinutes: Int = 5,
        forceRefresh: Boolean = false
    ): CandleSeries {

        require(intervalMinutes in listOf(5, 15, 30, 60, 240)) {
            "تایم‌فریم انتخاب‌شده معتبر نیست"
        }

        val cacheKey = "intraday_$intervalMinutes"
        val duration = cacheDuration(intervalMinutes)
        val mutex = candleLocks.getOrPut(cacheKey) { Mutex() }

        return mutex.withLock {

            if (!forceRefresh) {
                getCachedSeries(cacheKey, duration)?.let {
                    return@withLock it
                }
            }

            val result = withContext(Dispatchers.IO) {

                val days = historyDays(intervalMinutes)

                val url = "$WORKER_URL" +
                    "?symbol=IRTKMOFD0001" +
                    "&resolution=$intervalMinutes" +
                    "&days=$days"

                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->

                    if (!response.isSuccessful) {
                        error("خطای دریافت کندل از مفید: ${response.code}")
                    }

                    val body = response.body?.string()
                        ?: error("پاسخی از Worker دریافت نشد")

                    val root = JSONObject(body)

                    if (root.optString("s") != "ok") {
                        error(
                            root.optString(
                                "note",
                                "دریافت تاریخچه کندل از مفید ناموفق بود"
                            )
                        )
                    }

                    val timestamps = root.optJSONArray("t")
                        ?: error("زمان کندل‌ها در پاسخ وجود ندارد")

                    val opens = root.optJSONArray("o")
                        ?: error("قیمت بازشدن کندل‌ها وجود ندارد")

                    val highs = root.optJSONArray("h")
                        ?: error("بالاترین قیمت کندل‌ها وجود ندارد")

                    val lows = root.optJSONArray("l")
                        ?: error("پایین‌ترین قیمت کندل‌ها وجود ندارد")

                    val closes = root.optJSONArray("c")
                        ?: error("قیمت بسته‌شدن کندل‌ها وجود ندارد")

                    val volumes = root.optJSONArray("v")
                        ?: error("حجم معاملات کندل‌ها وجود ندارد")

                    val count = minOf(
                        timestamps.length(),
                        opens.length(),
                        highs.length(),
                        lows.length(),
                        closes.length(),
                        volumes.length()
                    )

                    val candles = mutableListOf<Candle>()

                    for (i in 0 until count) {
                        val timestampSeconds = timestamps.optLong(i, 0L)

                        val open = opens.optDouble(i, 0.0)
                        val high = highs.optDouble(i, 0.0)
                        val low = lows.optDouble(i, 0.0)
                        val close = closes.optDouble(i, 0.0)
                        val volume = volumes.optDouble(i, 0.0)

                        if (
                            timestampSeconds <= 0L ||
                            open <= 0.0 ||
                            high <= 0.0 ||
                            low <= 0.0 ||
                            close <= 0.0 ||
                            high < low
                        ) {
                            continue
                        }

                        val openTime = timestampSeconds * 1000L

                        candles.add(
                            Candle(
                                openTime = openTime,
                                open = open,
                                high = high,
                                low = low,
                                close = close,
                                volume = volume.coerceAtLeast(0.0),
                                closeTime = openTime +
                                    intervalMinutes * 60_000L - 1L
                            )
                        )
                    }

                    val sortedCandles = candles
                        .distinctBy { it.openTime }
                        .sortedBy { it.openTime }
                        .takeLast(TARGET_INTRADAY_CANDLES)

                    if (sortedCandles.isEmpty()) {
                        error("هیچ کندل معتبری از مفید دریافت نشد")
                    }

                    CandleSeries(
                        symbol = "عیار",
                        interval = "${intervalMinutes}m",
                        candles = sortedCandles
                    )
                }
            }

            candleCache[cacheKey] = CachedSeries(
                series = result,
                savedAtMillis = System.currentTimeMillis()
            )

            result
        }
    }

    // تاریخچه روزانه نیز در همان حافظه مشترک نگهداری می‌شود.
    suspend fun getDailyCandles(
        count: Int = 500,
        forceRefresh: Boolean = false
    ): CandleSeries {

        require(count in 1..5000) {
            "تعداد کندل‌های درخواستی معتبر نیست"
        }

        val cacheKey = "daily_$count"
        val duration = 12 * 60 * 60_000L
        val mutex = candleLocks.getOrPut(cacheKey) { Mutex() }

        return mutex.withLock {

            if (!forceRefresh) {
                getCachedSeries(cacheKey, duration)?.let {
                    return@withLock it
                }
            }

            val result = withContext(Dispatchers.IO) {

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

            candleCache[cacheKey] = CachedSeries(
                series = result,
                savedAtMillis = System.currentTimeMillis()
            )

            result
        }
    }

    // پاک‌کردن حافظه در صورت نیاز به دریافت اجباری داده جدید
    fun clearCandleCache(intervalMinutes: Int? = null) {
        if (intervalMinutes == null) {
            candleCache.clear()
        } else {
            candleCache.remove("intraday_$intervalMinutes")
        }
    }
}

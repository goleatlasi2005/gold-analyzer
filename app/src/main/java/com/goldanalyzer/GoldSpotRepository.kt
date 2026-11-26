
package com.goldanalyzer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

data class GoldSpotQuote(
    val priceUsdPerTroyOunce: Double,
    val source: String,
    val observedAtMillis: Long,
    val ageSeconds: Long,
    val isFresh: Boolean,
    val previousDailyCloseUsd: Double?,
    val changePercent: Double? = null
)

class GoldSpotRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    private val historyLock = Any()
    private var previousCloseCache: Double? = null
    private var historyFetchedAtMillis = 0L

    companion object {
        private const val MAX_PRICE_AGE_SECONDS = 180L
        private const val MAX_FUTURE_TOLERANCE_MILLIS = 60_000L
        private const val HISTORY_CACHE_MILLIS = 6 * 60 * 60 * 1000L
    }

    suspend fun getXauUsdSpot(): GoldSpotQuote =
        withContext(Dispatchers.IO) {

            val request = Request.Builder()
                .url("https://xaus.com/api/v1/spot?compact=1")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    error("خطای دریافت قیمت انس: ${response.code}")
                }

                val body = response.body?.string()
                    ?: error("پاسخی دریافت نشد")

                val root = JSONObject(body)
                val xau = root.optJSONObject("xau")

                val rootPrice = root.optDouble(
                    "spot_usd_oz",
                    Double.NaN
                )

                val nestedPrice = xau?.optDouble(
                    "spot_usd_oz",
                    Double.NaN
                ) ?: Double.NaN

                val price = when {
                    rootPrice.isFinite() && rootPrice > 0.0 ->
                        rootPrice

                    nestedPrice.isFinite() && nestedPrice > 0.0 ->
                        nestedPrice

                    else ->
                        error("قیمت دریافتی معتبر نیست")
                }

                val state = root.optJSONObject("data_state")
                    ?: xau?.optJSONObject("data_state")
                    ?: JSONObject()

                val status = state.optString("status", "")

                val priceTime = root.optString("price_as_of")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?: state.optString("as_of")
                        .takeIf { it.isNotBlank() && it != "null" }
                    ?: error("زمان ثبت قیمت مشخص نیست")

                val responseTime = root.optString("updated_at")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?: error("زمان پاسخ مشخص نیست")

                fun parseTime(value: String): Long {
                    return try {
                        Instant.parse(value).toEpochMilli()
                    } catch (_: Exception) {
                        error("زمان دریافتی معتبر نیست")
                    }
                }

                val observedAt = parseTime(priceTime)
                val updatedAt = parseTime(responseTime)
                val now = System.currentTimeMillis()

                if (observedAt <= 0L || updatedAt <= 0L) {
                    error("زمان دریافتی معتبر نیست")
                }

                if (
                    observedAt > now + MAX_FUTURE_TOLERANCE_MILLIS ||
                    updatedAt > now + MAX_FUTURE_TOLERANCE_MILLIS
                ) {
                    error("زمان قیمت دریافتی در آینده قرار دارد")
                }

                val calculatedAge = (now - observedAt) / 1000L
                val responseAge = (now - updatedAt) / 1000L

                val apiAge = if (state.has("age_seconds")) {
                    state.optLong("age_seconds", -1L)
                } else {
                    calculatedAge
                }

                if (apiAge < 0L) {
                    error("سن قیمت دریافتی معتبر نیست")
                }

                val age = maxOf(
                    apiAge,
                    calculatedAge,
                    responseAge,
                    0L
                )

                val stale = root.optBoolean("stale", true)

                val fresh =
                    !stale &&
                    age <= MAX_PRICE_AGE_SECONDS &&
                    status == "fresh"

                GoldSpotQuote(
                    priceUsdPerTroyOunce = price,
                    source = root.optString(
                        "price_source",
                        "XAUS"
                    ).ifBlank { "XAUS" },
                    observedAtMillis = observedAt,
                    ageSeconds = age,
                    isFresh = fresh,
                    previousDailyCloseUsd = getPreviousDailyClose(),
                    changePercent = null
                )
            }
        }

    private suspend fun getPreviousDailyClose(): Double? =
        withContext(Dispatchers.IO) {

            val now = System.currentTimeMillis()

            synchronized(historyLock) {
                if (
                    previousCloseCache != null &&
                    now >= historyFetchedAtMillis &&
                    now - historyFetchedAtMillis < HISTORY_CACHE_MILLIS
                ) {
                    return@withContext previousCloseCache
                }
            }

            try {
                val request = Request.Builder()
                    .url("https://xaus.com/api/v1/history")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                val result = httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use null
                    }

                    val body = response.body?.string()
                        ?: return@use null

                    val points = JSONObject(body).optJSONArray("points")
                        ?: return@use null

                    val todayUtc = LocalDate.now(ZoneOffset.UTC)

                    var previous: Pair<LocalDate, Double>? = null

                    for (i in 0 until points.length()) {
                        val item = points.optJSONObject(i) ?: continue

                        val date = try {
                            LocalDate.parse(item.optString("d"))
                        } catch (_: Exception) {
                            continue
                        }

                        val close = item.optDouble("c", Double.NaN)

                        if (
                            date.isBefore(todayUtc) &&
                            close.isFinite() &&
                            close > 0.0
                        ) {
                            if (
                                previous == null ||
                                date.isAfter(previous.first)
                            ) {
                                previous = date to close
                            }
                        }
                    }

                    previous?.second
                }

                synchronized(historyLock) {
                    previousCloseCache = result
                    historyFetchedAtMillis = now
                }

                result
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
}

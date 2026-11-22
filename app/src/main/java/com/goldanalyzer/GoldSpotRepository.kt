
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val historyLock = Any()
    private var previousCloseCache: Double? = null
    private var historyFetchedAtMillis = 0L

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

                val price = root.optDouble(
                    "spot_usd_oz",
                    Double.NaN
                ).takeIf { it.isFinite() && it > 0.0 }
                    ?: xau?.optDouble(
                        "spot_usd_oz",
                        Double.NaN
                    )
                    ?: Double.NaN

                if (!price.isFinite() || price <= 0.0) {
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

                val calculatedAge = (now - observedAt) / 1000L

                val apiAge = state.optLong(
                    "age_seconds",
                    calculatedAge
                )

                val responseAge = (now - updatedAt) / 1000L

                val age = maxOf(
                    0L,
                    apiAge,
                    calculatedAge,
                    responseAge
                )

                val stale = root.optBoolean("stale", true)

                val fresh =
                    !stale &&
                    age in 0..180 &&
                    status == "fresh"

                GoldSpotQuote(
                    priceUsdPerTroyOunce = price,
                    source = root.optString(
                        "price_source",
                        "XAUS"
                    ),
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
                    now - historyFetchedAtMillis < 6 * 60 * 60 * 1000L
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

                        val close = item.optDouble("c", 0.0)

                        if (date.isBefore(todayUtc) && close > 0.0) {
                            if (
                                previous == null ||
                                date.isAfter(previous!!.first)
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
            } catch (_: Exception) {
                null
            }
        }
}

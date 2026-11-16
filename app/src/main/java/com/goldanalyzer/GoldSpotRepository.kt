
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant

data class GoldSpotQuote(
    val priceUsdPerTroyOunce: Double,
    val source: String,
    val observedAtMillis: Long,
    val ageSeconds: Long,
    val isFresh: Boolean
)

class GoldSpotRepository(
    private val httpClient: OkHttpClient = OkHttpClient()
) {
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
                    isFresh = fresh
                )
            }
        }
}

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

                val json = JSONObject(body)

                val state = json.optJSONObject("data_state")
                    ?: error("وضعیت داده مشخص نیست")

                val status = state.optString("status", "unavailable")

                if (status == "unavailable") {
                    error("قیمت انس در دسترس نیست")
                }

                val price = json.optDouble("spot_usd_oz", Double.NaN)

                if (!price.isFinite() || price <= 0.0) {
                    error("قیمت دریافتی معتبر نیست")
                }

                val timestamp = json.optString("price_as_of")
                    .takeIf { it.isNotBlank() && it != "null" }
                    ?: state.optString("as_of")
                        .takeIf { it.isNotBlank() && it != "null" }
                    ?: error("زمان قیمت مشخص نیست")

                val observedAt = try {
                    Instant.parse(timestamp).toEpochMilli()
                } catch (_: Exception) {
                    error("زمان قیمت معتبر نیست")
                }

                val age = (
                    (System.currentTimeMillis() - observedAt) / 1000L
                ).coerceAtLeast(0L)

                GoldSpotQuote(
                    priceUsdPerTroyOunce = price,
                    source = json.optString("price_source", "XAUS"),
                    observedAtMillis = observedAt,
                    ageSeconds = age,
                    isFresh = status == "fresh" && age <= 180L
                )
            }
        }
}

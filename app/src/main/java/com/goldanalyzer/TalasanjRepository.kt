
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class TalasanjPrices(
    val gold18Toman: Long,
    val mesghalToman: Long,
    val ounceUsd: Double,
    val gold18ChangePercent: Double?,
    val mesghalChangePercent: Double?,
    val ounceChangePercent: Double?,
    val updatedAt: String,
    val marketOpen: Boolean
)

class TalasanjRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    suspend fun getPrices(): TalasanjPrices =
        withContext(Dispatchers.IO) {

            val request = Request.Builder()
                .url("https://damp-tooth-c9ca.gole-atlasi2005.workers.dev/")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    error("خطای دریافت اطلاعات طلاسنج: HTTP ${response.code}")
                }

                val body = response.body?.string()
                    ?: error("پاسخی از طلاسنج دریافت نشد.")

                val root = JSONObject(body)
                val prices = root.getJSONObject("prices")

                val gold18Object = prices.getJSONObject("gram18")
                val mesghalObject = prices.getJSONObject("mazaneh")
                val ounceObject = prices.getJSONObject("ounce")

                val gold18 = gold18Object.getLong("price")
                val mesghal = mesghalObject.getLong("price")
                val ounce = ounceObject.getDouble("price")

                fun readChangePercent(item: JSONObject): Double? {
                    return item.optDouble("change_pct", Double.NaN)
                        .takeIf { it.isFinite() }
                }

                if (gold18 <= 0L || mesghal <= 0L || ounce <= 0.0) {
                    error("قیمت دریافتی طلاسنج معتبر نیست.")
                }

                TalasanjPrices(
                    gold18Toman = gold18,
                    mesghalToman = mesghal,
                    ounceUsd = ounce,
                    gold18ChangePercent = readChangePercent(gold18Object),
                    mesghalChangePercent = readChangePercent(mesghalObject),
                    ounceChangePercent = readChangePercent(ounceObject),
                    updatedAt = root.optString("updated_at", ""),
                    marketOpen = root.optBoolean("market_open", false)
                )
            }
        }
}

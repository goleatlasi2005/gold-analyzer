
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
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
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

                if (body.isBlank()) {
                    error("پاسخ طلاسنج خالی است.")
                }

                val root = JSONObject(body)

                val prices = root.optJSONObject("prices")
                    ?: error("ساختار قیمت‌های طلاسنج معتبر نیست.")

                val gold18Object = prices.optJSONObject("gram18")
                    ?: error("قیمت طلای ۱۸ عیار دریافت نشد.")

                val mesghalObject = prices.optJSONObject("mazaneh")
                    ?: error("قیمت مظنه دریافت نشد.")

                val ounceObject = prices.optJSONObject("ounce")
                    ?: error("قیمت انس دریافت نشد.")

                fun readPositiveLong(
                    item: JSONObject,
                    field: String
                ): Long {
                    val value = item.optDouble(field, Double.NaN)

                    if (
                        !value.isFinite() ||
                        value <= 0.0 ||
                        value > Long.MAX_VALUE.toDouble() ||
                        value % 1.0 != 0.0
                    ) {
                        error("مقدار دریافتی قیمت طلاسنج معتبر نیست.")
                    }

                    return value.toLong()
                }

                fun readPositiveDouble(
                    item: JSONObject,
                    field: String
                ): Double {
                    val value = item.optDouble(field, Double.NaN)

                    if (!value.isFinite() || value <= 0.0) {
                        error("قیمت دریافتی انس معتبر نیست.")
                    }

                    return value
                }

                fun readChangePercent(item: JSONObject): Double? {
                    if (!item.has("change_pct")) return null

                    val value = item.optDouble(
                        "change_pct",
                        Double.NaN
                    )

                    return value.takeIf {
                        it.isFinite()
                    }
                }

                val gold18 = readPositiveLong(
                    gold18Object,
                    "price"
                )

                val mesghal = readPositiveLong(
                    mesghalObject,
                    "price"
                )

                val ounce = readPositiveDouble(
                    ounceObject,
                    "price"
                )

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

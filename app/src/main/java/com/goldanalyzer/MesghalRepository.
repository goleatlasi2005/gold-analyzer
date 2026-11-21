
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class MesghalQuote(
    val currentPriceRial: Double,
    val changeRial: Double,
    val changePercent: Double,
    val highRial: Double,
    val lowRial: Double,
    val observedAtMillis: Long,
    val ageSeconds: Long,
    val isFresh: Boolean
)

class MesghalRepository(
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    suspend fun getLivePrice(): MesghalQuote =
        withContext(Dispatchers.IO) {

            val request = Request.Builder()
                .url("https://call1.tgju.org/ajax.json")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    error("خطای دریافت قیمت مثقال از TGJU: ${response.code}")
                }

                val body = response.body?.string()
                    ?: error("پاسخی از TGJU دریافت نشد.")

                val root = JSONObject(body)
                val data = root.optJSONObject("current") ?: root

                val item = data.optJSONObject("mesghal")
                    ?: error("اطلاعات مثقال طلا در پاسخ TGJU پیدا نشد.")

                fun readNumber(key: String): Double {
                    val raw = item.optString(key, "")
                        .replace(",", "")
                        .trim()

                    return raw.toDoubleOrNull()
                        ?: error("مقدار معتبر برای $key دریافت نشد.")
                }

                // قیمت‌های مثقال TGJU مستقیماً به ریال هستند.
                val price = readNumber("p")
                val high = readNumber("h")
                val low = readNumber("l")
                val change = readNumber("d")
                val percent = readNumber("dp")

                if (price <= 0.0 || high <= 0.0 || low <= 0.0) {
                    error("قیمت دریافتی مثقال معتبر نیست.")
                }

                val timestamp = item.optString("ts", "")

                if (timestamp.isBlank()) {
                    error("زمان ثبت قیمت مثقال مشخص نیست.")
                }

                val formatter = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.US
                ).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Tehran")
                    isLenient = false
                }

                val observedAt = formatter.parse(timestamp)?.time
                    ?: error("زمان ثبت قیمت قابل پردازش نیست.")

                val age = (
                    System.currentTimeMillis() - observedAt
                ) / 1000L

                MesghalQuote(
                    currentPriceRial = price,
                    changeRial = change,
                    changePercent = percent,
                    highRial = high,
                    lowRial = low,
                    observedAtMillis = observedAt,
                    ageSeconds = age.coerceAtLeast(0L),
                    isFresh = age in 0..180
                )
            }
        }
}

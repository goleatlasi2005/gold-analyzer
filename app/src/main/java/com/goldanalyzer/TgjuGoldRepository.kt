
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class TgjuGoldQuote(
    val currentPriceToman: Double,
    val changeToman: Double,
    val changePercent: Double,
    val highToman: Double,
    val lowToman: Double,
    val observedAtMillis: Long,
    val ageSeconds: Long,
    val isFresh: Boolean
)

class TgjuGoldRepository(
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    suspend fun getLivePrice(): TgjuGoldQuote =
        withContext(Dispatchers.IO) {

            val request = Request.Builder()
                .url("https://call1.tgju.org/ajax.json")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    error("خطای دریافت قیمت طلای ۱۸ عیار از TGJU: ${response.code}")
                }

                val body = response.body?.string()
                    ?: error("پاسخی از TGJU دریافت نشد.")

                val root = JSONObject(body)

                val item = root.optJSONObject("tgju_gold_irg18")
                    ?: error("اطلاعات طلای ۱۸ عیار در پاسخ TGJU پیدا نشد.")

                fun readNumber(key: String): Double {
                    val raw = item.optString(key, "")
                        .replace(",", "")
                        .trim()

                    return raw.toDoubleOrNull()
                        ?: error("مقدار معتبر برای $key دریافت نشد.")
                }

                // قیمت‌های TGJU به ریال هستند؛ تبدیل به تومان
                val price = readNumber("p") / 10.0
                val high = readNumber("h") / 10.0
                val low = readNumber("l") / 10.0
                val change = readNumber("d") / 10.0
                val percent = readNumber("dp")

                if (
                    price <= 0.0 ||
                    high <= 0.0 ||
                    low <= 0.0
                ) {
                    error("قیمت دریافتی از TGJU معتبر نیست.")
                }

                val timestamp = item.optString("ts", "")

                if (timestamp.isBlank()) {
                    error("زمان ثبت قیمت TGJU مشخص نیست.")
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

                TgjuGoldQuote(
                    currentPriceToman = price,
                    changeToman = change,
                    changePercent = percent,
                    highToman = high,
                    lowToman = low,
                    observedAtMillis = observedAt,
                    ageSeconds = age.coerceAtLeast(0L),
                    isFresh = age in 0..180
                )
            }
        }
}

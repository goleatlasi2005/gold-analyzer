
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

data class MesghalQuote(
    val currentPriceRial: Double,
    val changeRial: Double,
    val changePercent: Double?,
    val highRial: Double,
    val lowRial: Double,
    val observedAtMillis: Long,
    val ageSeconds: Long,
    val isFresh: Boolean
)

data class MesghalPrices(
    val talasanj: MesghalQuote?,
    val tgju: MesghalQuote?,
    val nerkhAra: MesghalQuote?
)

class MesghalRepository(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val talasanjRepository: TalasanjRepository = TalasanjRepository()
) {

    suspend fun getLivePrices(): MesghalPrices {

        val talasanjPrice = try {
            val prices = talasanjRepository.getPrices()
            val observedAt = try {
                Instant.parse(prices.updatedAt).toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }

            val age = (
                System.currentTimeMillis() - observedAt
            ) / 1000L

            MesghalQuote(
                currentPriceRial = prices.mesghalToman * 10.0,
                changeRial = 0.0,
                changePercent = prices.mesghalChangePercent,
                highRial = 0.0,
                lowRial = 0.0,
                observedAtMillis = observedAt,
                ageSeconds = age.coerceAtLeast(0L),
                isFresh = age in 0..900
            )
        } catch (_: Exception) {
            null
        }

        val tgjuPrice = try {
            getLivePrice()
        } catch (_: Exception) {
            null
        }

        val nerkhAraPrice = try {
            getNerkhAraPrice()
        } catch (_: Exception) {
            null
        }

        return MesghalPrices(
            talasanj = talasanjPrice,
            tgju = tgjuPrice,
            nerkhAra = nerkhAraPrice
        )
    }

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

    suspend fun getNerkhAraPrice(): MesghalQuote =
        withContext(Dispatchers.IO) {

            val request = Request.Builder()
                .url("https://nerkhara.com/api/v1/latest/gold-mesghal.json")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    error("خطای دریافت قیمت مظنه از نرخ‌آرا: ${response.code}")
                }

                val body = response.body?.string()
                    ?: error("پاسخی از نرخ‌آرا دریافت نشد.")

                val root = JSONObject(body)
                val item = root.optJSONObject("item")
                    ?: error("اطلاعات مظنه در نرخ‌آرا پیدا نشد.")

                val priceToman = item.optDouble("price", 0.0)

                if (priceToman <= 0.0) {
                    error("قیمت دریافتی نرخ‌آرا معتبر نیست.")
                }

                val changePercent = item.optDouble("change_pct", 0.0)
                val timestamp = item.optString("updated_utc", "")

                if (timestamp.isBlank()) {
                    error("زمان ثبت قیمت نرخ‌آرا مشخص نیست.")
                }

                val formatter = SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    Locale.US
                ).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }

                val observedAt = formatter.parse(timestamp)?.time
                    ?: error("زمان ثبت قیمت نرخ‌آرا قابل پردازش نیست.")

                val age = (
                    System.currentTimeMillis() - observedAt
                ) / 1000L

                val stale = item.optBoolean("stale", true)

                MesghalQuote(
                    currentPriceRial = priceToman * 10.0,
                    changeRial = 0.0,
                    changePercent = changePercent,
                    highRial = 0.0,
                    lowRial = 0.0,
                    observedAtMillis = observedAt,
                    ageSeconds = age.coerceAtLeast(0L),
                    isFresh = !stale && age in 0..900
                )
            }
        }
}

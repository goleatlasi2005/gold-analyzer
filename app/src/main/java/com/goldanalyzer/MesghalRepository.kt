
package com.goldanalyzer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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

    companion object {
        private const val FUTURE_TOLERANCE_MILLIS = 60_000L
        private const val TALASANJ_MAX_AGE_SECONDS = 900L
        private const val TGJU_MAX_AGE_SECONDS = 180L
        private const val NERKHARA_MAX_AGE_SECONDS = 900L
    }

    private fun calculateAgeSeconds(observedAt: Long): Long {
        val now = System.currentTimeMillis()

        if (observedAt <= 0L) {
            error("زمان ثبت قیمت معتبر نیست.")
        }

        if (observedAt > now + FUTURE_TOLERANCE_MILLIS) {
            error("زمان ثبت قیمت در آینده قرار دارد.")
        }

        return ((now - observedAt) / 1000L).coerceAtLeast(0L)
    }

    private fun parseUtcTimestamp(value: String): Long {
        if (value.isBlank()) {
            error("زمان ثبت قیمت مشخص نیست.")
        }

        return try {
            Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(
                    value,
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
                ).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) {
                error("زمان ثبت قیمت قابل پردازش نیست.")
            }
        }
    }

    suspend fun getLivePrices(): MesghalPrices =
        kotlinx.coroutines.supervisorScope {

            val talasanjTask = async {
                try {
                    val prices = talasanjRepository.getPrices()

                    val observedAt = parseUtcTimestamp(prices.updatedAt)
                    val age = calculateAgeSeconds(observedAt)

                    val rialPrice = prices.mesghalToman * 10.0

                    if (!rialPrice.isFinite() || rialPrice <= 0.0) {
                        error("قیمت مظنه طلاسنج معتبر نیست.")
                    }

                    MesghalQuote(
                        currentPriceRial = rialPrice,
                        changeRial = 0.0,
                        changePercent = prices.mesghalChangePercent,
                        highRial = 0.0,
                        lowRial = 0.0,
                        observedAtMillis = observedAt,
                        ageSeconds = age,
                        isFresh = age <= TALASANJ_MAX_AGE_SECONDS
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }

            val tgjuTask = async {
                try {
                    getLivePrice()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }

            val nerkhAraTask = async {
                try {
                    getNerkhAraPrice()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }

            MesghalPrices(
                talasanj = talasanjTask.await(),
                tgju = tgjuTask.await(),
                nerkhAra = nerkhAraTask.await()
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

                    val value = raw.toDoubleOrNull()
                        ?: error("مقدار معتبر برای $key دریافت نشد.")

                    if (!value.isFinite()) {
                        error("مقدار دریافتی $key معتبر نیست.")
                    }

                    return value
                }

                val price = readNumber("p")
                val high = readNumber("h")
                val low = readNumber("l")
                val change = readNumber("d")
                val percent = readNumber("dp")

                if (
                    price <= 0.0 ||
                    high <= 0.0 ||
                    low <= 0.0 ||
                    high < low ||
                    price < low ||
                    price > high
                ) {
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

                val age = calculateAgeSeconds(observedAt)

                MesghalQuote(
                    currentPriceRial = price,
                    changeRial = change,
                    changePercent = percent,
                    highRial = high,
                    lowRial = low,
                    observedAtMillis = observedAt,
                    ageSeconds = age,
                    isFresh = age <= TGJU_MAX_AGE_SECONDS
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

                val priceToman = item.optDouble("price", Double.NaN)

                if (!priceToman.isFinite() || priceToman <= 0.0) {
                    error("قیمت دریافتی نرخ‌آرا معتبر نیست.")
                }

                val priceRial = priceToman * 10.0

                if (!priceRial.isFinite() || priceRial <= 0.0) {
                    error("قیمت ریالی نرخ‌آرا معتبر نیست.")
                }

                val changePercent = if (item.has("change_pct")) {
                    item.optDouble("change_pct", Double.NaN)
                        .takeIf { it.isFinite() }
                } else {
                    null
                }

                val timestamp = item.optString("updated_utc", "")
                val observedAt = parseUtcTimestamp(timestamp)
                val age = calculateAgeSeconds(observedAt)

                val stale = item.optBoolean("stale", true)

                MesghalQuote(
                    currentPriceRial = priceRial,
                    changeRial = 0.0,
                    changePercent = changePercent,
                    highRial = 0.0,
                    lowRial = 0.0,
                    observedAtMillis = observedAt,
                    ageSeconds = age,
                    isFresh = !stale && age <= NERKHARA_MAX_AGE_SECONDS
                )
            }
        }
}

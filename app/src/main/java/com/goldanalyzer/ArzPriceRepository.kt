package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.TimeZone

data class ArzPriceQuote(
    val currentPriceToman: Double,
    val observedAtMillis: Long,
    val ageSeconds: Long,
    val isFresh: Boolean
)

class ArzPriceRepository(
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    companion object {
        private const val PRICE_URL =
            "https://ibahoosham.ir/arzprice/w-api/category/gold"

        private const val TARGET_NAME = "طلا گرم 18 عیار"
    }

    suspend fun getLivePrice(): ArzPriceQuote =
        withContext(Dispatchers.IO) {

            val request = Request.Builder()
                .url(PRICE_URL)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    error("خطای دریافت قیمت از ارزپرایس: ${response.code}")
                }

                val body = response.body?.string()
                    ?: error("پاسخی از ارزپرایس دریافت نشد.")

                val root = JSONObject(body)

                if (!root.optBoolean("ok", true)) {
                    error("ارزپرایس پاسخ معتبر ارسال نکرد.")
                }

                val item = findGoldItem(root)
                    ?: error("قیمت طلای ۱۸ عیار در ارزپرایس پیدا نشد.")

                val rawPrice = readNumber(item, "price")
                    ?: error("قیمت معتبر از ارزپرایس دریافت نشد.")

                if (rawPrice <= 0.0) {
                    error("قیمت ارزپرایس نامعتبر است.")
                }

                val unit = item.optString("unit", "")
                    .lowercase(Locale.ROOT)

                val priceToman = when {
                    unit.contains("toman") ||
                    unit.contains("تومان") -> rawPrice

                    else -> rawPrice / 10.0
                }

                val timestamp = readTimestamp(item)
                val observedAt = parseTimestamp(timestamp)

                val age = (
                    System.currentTimeMillis() - observedAt
                ) / 1000L

                ArzPriceQuote(
                    currentPriceToman = priceToman,
                    observedAtMillis = observedAt,
                    ageSeconds = age.coerceAtLeast(0L),
                    isFresh = age in 0..300
                )
            }
        }

    private fun findGoldItem(root: JSONObject): JSONObject? {
        return findGoldItemInValue(root)
    }

    private fun findGoldItemInValue(value: Any?): JSONObject? {

        when (value) {
            is JSONObject -> {

                val name = sequenceOf(
                    "name",
                    "symbol",
                    "title",
                    "label"
                ).map {
                    value.optString(it, "")
                }.firstOrNull {
                    it.isNotBlank()
                } ?: ""

                val normalized = name
                    .replace("ي", "ی")
                    .replace("ك", "ک")
                    .replace("۱۸", "18")
                    .trim()

                val hasPrice = value.has("price")

                if (
                    hasPrice &&
                    normalized.contains("طلا") &&
                    normalized.contains("18")
                ) {
                    return value
                }

                val keys = value.keys()

                while (keys.hasNext()) {
                    val key = keys.next()
                    val found = findGoldItemInValue(
                        value.opt(key)
                    )

                    if (found != null) return found
                }
            }

            is JSONArray -> {
                for (i in 0 until value.length()) {
                    val found = findGoldItemInValue(
                        value.opt(i)
                    )

                    if (found != null) return found
                }
            }
        }

        return null
    }

    private fun readNumber(
        item: JSONObject,
        key: String
    ): Double? {

        val raw = item.optString(key, "")
            .replace(",", "")
            .replace("٬", "")
            .trim()

        return raw.toDoubleOrNull()
    }

    private fun readTimestamp(item: JSONObject): String {

        val keys = listOf(
            "timestamp",
            "updated_at",
            "updatedAt",
            "lastUpdate",
            "date",
            "time"
        )

        for (key in keys) {
            val value = item.optString(key, "")
            if (value.isNotBlank() && value != "null") {
                return value
            }
        }

        error("زمان ثبت قیمت ارزپرایس مشخص نیست.")
    }

    private fun parseTimestamp(value: String): Long {

        try {
            return OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            // تلاش با قالب‌های دیگر
        }

        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS"
        )

        for (pattern in formats) {
            try {
                val formatter = SimpleDateFormat(
                    pattern,
                    Locale.US
                ).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Tehran")
                    isLenient = false
                }

                val parsed = formatter.parse(value)
                if (parsed != null) return parsed.time

            } catch (_: Exception) {
                // قالب بعدی بررسی شود
            }
        }

        error("زمان ثبت قیمت ارزپرایس قابل پردازش نیست.")
    }
}


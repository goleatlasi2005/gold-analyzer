
package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class TalineGoldQuote(
    val buyPriceToman: Long,
    val sellPriceToman: Long,
    val receivedAtMillis: Long
)

class TalineGoldRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
) {

    suspend fun getLivePrice(): TalineGoldQuote =
        withContext(Dispatchers.IO) {

            val request = Request.Builder()
                .url("https://taline.ir/")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml"
                )
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    error("خطای دریافت اطلاعات طلاین: HTTP ${response.code}")
                }

                val html = response.body?.string()
                    ?: error("پاسخی از سایت طلاین دریافت نشد.")

                val pattern = Regex(
                    """goldPrices\s*=\s*\{\s*buy:\s*(\d+),\s*sell:\s*(\d+)"""
                )

                val match = pattern.find(html)
                    ?: error(
                        "اطلاعات قیمت در صفحه طلاین پیدا نشد؛ ممکن است ساختار سایت تغییر کرده باشد."
                    )

                val buy = match.groupValues[1].toLongOrNull()
                    ?: error("قیمت خرید طلاین معتبر نیست.")

                val sell = match.groupValues[2].toLongOrNull()
                    ?: error("قیمت فروش طلاین معتبر نیست.")

                if (buy <= 0L || sell <= 0L) {
                    error(
                        "قیمت طلاین برای این اتصال اینترنتی در دسترس نیست."
                    )
                }

                TalineGoldQuote(
                    buyPriceToman = buy,
                    sellPriceToman = sell,
                    receivedAtMillis = System.currentTimeMillis()
                )
            }
        }
}

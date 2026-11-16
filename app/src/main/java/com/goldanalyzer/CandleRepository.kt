package com.goldanalyzer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

private val candleHttpClient = OkHttpClient()

class CandleRepository {

    suspend fun getBinanceCandles(
        symbol: String,
        interval: String,
        limit: Int = 200
    ): CandleSeries = withContext(Dispatchers.IO) {

        val request = Request.Builder()
            .url(
                "https://api.binance.com/api/v3/klines" +
                "?symbol=${symbol.uppercase()}USDT" +
                "&interval=$interval&limit=$limit"
            )
            .build()

        candleHttpClient.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                error("خطای Binance: ${response.code}")
            }

            val json = JSONArray(
                response.body?.string() ?: error("پاسخی دریافت نشد")
            )

            val candles = (0 until json.length()).map { index ->
                val item = json.getJSONArray(index)

                Candle(
                    openTime = item.getLong(0),
                    open = item.getString(1).toDouble(),
                    high = item.getString(2).toDouble(),
                    low = item.getString(3).toDouble(),
                    close = item.getString(4).toDouble(),
                    volume = item.getString(5).toDouble(),
                    closeTime = item.getLong(6)
                )
            }

            CandleSeries(
                symbol = symbol.uppercase(),
                interval = interval,
                candles = candles
            )
        }
    }

    suspend fun getNobitexCandles(
        symbol: String,
        interval: String,
        limit: Int = 200
    ): CandleSeries = withContext(Dispatchers.IO) {

        val resolution = when (interval) {
            "5m" -> 5
            "15m" -> 15
            "1h" -> 60
            "4h" -> 240
            "1d" -> 1440
            "1w" -> 10080
            else -> error("تایم‌فریم پشتیبانی نمی‌شود")
        }

        val end = System.currentTimeMillis() / 1000
        val start = end - resolution * 60L * limit

        val request = Request.Builder()
            .url(
                "https://apiv2.nobitex.ir/market/udf/history" +
                "?symbol=${symbol.uppercase()}USDT" +
                "&resolution=$resolution" +
                "&from=$start&to=$end"
            )
            .build()

        candleHttpClient.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                error("خطای Nobitex: ${response.code}")
            }

            val json = org.json.JSONObject(
                response.body?.string() ?: error("پاسخی دریافت نشد")
            )

            if (json.optString("s") != "ok") {
                error("اطلاعات کندل از Nobitex دریافت نشد")
            }

            val times = json.getJSONArray("t")
            val opens = json.getJSONArray("o")
            val highs = json.getJSONArray("h")
            val lows = json.getJSONArray("l")
            val closes = json.getJSONArray("c")
            val volumes = json.getJSONArray("v")

            val candles = (0 until times.length()).map { index ->
                val openTime = times.getLong(index) * 1000L

                Candle(
                    openTime = openTime,
                    open = opens.getDouble(index),
                    high = highs.getDouble(index),
                    low = lows.getDouble(index),
                    close = closes.getDouble(index),
                    volume = volumes.getDouble(index),
                    closeTime = openTime + resolution * 60_000L - 1
                )
            }

            CandleSeries(
                symbol = symbol.uppercase(),
                interval = interval,
                candles = candles
            )
        }
    }
}


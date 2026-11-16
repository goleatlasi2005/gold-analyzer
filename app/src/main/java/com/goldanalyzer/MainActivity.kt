
package com.goldanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CryptoAnalyzerApp() }
    }
}

private data class MarketTicker(
    val price: Double,
    val change: Double,
    val high: Double,
    val low: Double,
    val volume: Double
)

private val client = OkHttpClient()

private suspend fun fetchBinance(symbol: String): MarketTicker =
    withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.binance.com/api/v3/ticker/24hr?symbol=$symbol")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("خطای Binance: ${response.code}")
            val json = JSONObject(
                response.body?.string() ?: error("پاسخی دریافت نشد")
            )
            MarketTicker(
                json.getString("lastPrice").toDouble(),
                json.getString("priceChangePercent").toDouble(),
                json.getString("highPrice").toDouble(),
                json.getString("lowPrice").toDouble(),
                json.getString("volume").toDouble()
            )
        }
    }

private suspend fun fetchNobitex(): Map<String, MarketTicker> =
    withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://apiv2.nobitex.ir/market/stats?srcCurrency=btc,eth&dstCurrency=usdt")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("خطای Nobitex: ${response.code}")
            val json = JSONObject(
                response.body?.string() ?: error("پاسخی دریافت نشد")
            )

            if (json.optString("status") != "ok") {
                error("پاسخ نامعتبر از Nobitex")
            }

            val stats = json.getJSONObject("stats")
            val result = mutableMapOf<String, MarketTicker>()

            listOf("btc", "eth").forEach { coin ->
                val item = stats.optJSONObject("$coin-usdt")
                    ?: error("اطلاعات ${coin.uppercase()} در نوبیتکس موجود نیست")

                result[coin.uppercase()] = MarketTicker(
                    item.getString("latest").toDouble(),
                    item.optString("dayChange", "0").toDouble(),
                    item.optString("dayHigh", "0").toDouble(),
                    item.optString("dayLow", "0").toDouble(),
                    item.optString("volumeSrc", "0").toDouble()
                )
            }
            result
        }
    }

private fun formatNumber(value: Double): String =
    DecimalFormat("#,##0.00").format(value)

@Composable
private fun ExchangeCard(
    exchange: String,
    coin: String,
    data: MarketTicker?,
    error: String?,
    updatedAt: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF29213D)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                exchange,
                color = Color(0xFFD8B4FE),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text("$coin / USDT", color = Color.White, fontSize = 16.sp)

            if (data != null) {
                Text(
                    "$${formatNumber(data.price)}",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "تغییر ۲۴ ساعته: ${
                        String.format(Locale.US, "%+.2f%%", data.change)
                    }",
                    color = if (data.change >= 0)
                        Color(0xFF4ADE80) else Color(0xFFF87171)
                )
                Text("بیشترین قیمت: $${formatNumber(data.high)}", color = Color.LightGray)
                Text("کمترین قیمت: $${formatNumber(data.low)}", color = Color.LightGray)
                Text("حجم: ${formatNumber(data.volume)} $coin", color = Color.LightGray)
                Text(
                    "آخرین به‌روزرسانی: $updatedAt",
                    color = Color(0xFFD8B4FE),
                    fontSize = 12.sp
                )
            } else {
                Text(
                    error ?: "در حال دریافت اطلاعات...",
                    color = if (error != null) Color(0xFFF87171) else Color.LightGray
                )
            }
        }
    }
}

@Composable
fun CryptoAnalyzerApp() {
    var selectedCoin by remember { mutableStateOf("BTC") }
    var showAnalysis by remember { mutableStateOf(false) }

    var binance by remember { mutableStateOf<MarketTicker?>(null) }
    var nobitex by remember { mutableStateOf<MarketTicker?>(null) }
    var binanceError by remember { mutableStateOf<String?>(null) }
    var nobitexError by remember { mutableStateOf<String?>(null) }
    var updatedAt by remember { mutableStateOf("") }

    var manualRefresh by remember { mutableStateOf(0) }

    LaunchedEffect(selectedCoin, manualRefresh) {
        while (true) {
            try {
                binance = fetchBinance("${selectedCoin}USDT")
                binanceError = null
            } catch (e: Exception) {
                binanceError = e.message ?: "خطا در دریافت Binance"
            }

            try {
                val all = fetchNobitex()
                nobitex = all[selectedCoin]
                nobitexError = null
            } catch (e: Exception) {
                nobitexError = e.message ?: "خطا در دریافت Nobitex"
            }

            updatedAt = SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault()
            ).format(Date())

            delay(15_000)
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF171126)
        ) {
            if (showAnalysis) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Button(
                        onClick = { showAnalysis = false },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9333EA)
                        )
                    ) {
                        Text("بازگشت به صفحه اصلی")
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        AnalysisScreen(symbol = selectedCoin)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            "Crypto Analyzer",
                            color = Color(0xFFE9D5FF),
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "تحلیل هوشمند بازار ارز دیجیتال",
                            color = Color.LightGray
                        )
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("BTC", "ETH").forEach { coin ->
                                Button(
                                    onClick = { selectedCoin = coin },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedCoin == coin)
                                            Color(0xFF9333EA) else Color(0xFF3B3150)
                                    )
                                ) {
                                    Text(coin)
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { manualRefresh++ },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9333EA)
                            )
                        ) {
                            Text("⟳ به‌روزرسانی قیمت‌ها")
                        }
                    }

                    item {
                        ExchangeCard(
                            "Binance",
                            selectedCoin,
                            binance,
                            binanceError,
                            updatedAt
                        )
                    }

                    item {
                        ExchangeCard(
                            "Nobitex",
                            selectedCoin,
                            nobitex,
                            nobitexError,
                            updatedAt
                        )
                    }

                    item {
                        val a = binance?.price
                        val b = nobitex?.price

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF29213D)
                            )
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text(
                                    "مقایسه قیمت دو صرافی",
                                    color = Color(0xFFD8B4FE),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (a != null && b != null) {
                                    Text(
                                        "اختلاف قیمت: $${formatNumber(kotlin.math.abs(a - b))}",
                                        color = Color.White
                                    )
                                } else {
                                    Text(
                                        "پس از دریافت قیمت هر دو صرافی نمایش داده می‌شود.",
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { showAnalysis = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9333EA)
                            )
                        ) {
                            Text(
                                "تحلیل تکنیکال $selectedCoin",
                                fontSize = 17.sp
                            )
                        }
                    }

                    item {
                        Text(
                            "تایم‌فریم‌ها: 5m • 15m • 1h • 4h • 1d • 1w",
                            color = Color.LightGray
                        )
                    }

                    item {
                        Text(
                            "RSI • MACD • EMA • SMA • Bollinger Bands • Ichimoku • Fibonacci • Volume",
                            color = Color.LightGray
                        )
                    }

                    item {
                        Text(
                            "این برنامه فقط تحلیل ارائه می‌دهد و هیچ معامله‌ای انجام نمی‌دهد.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

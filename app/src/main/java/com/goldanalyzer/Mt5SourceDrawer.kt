
package com.goldanalyzer

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Mt5Gold = Color(0xFFD4A52C)
private val Mt5Gray = Color(0xFF777777)
private val Mt5Red = Color(0xFFB3261E)
private val Mt5Green = Color(0xFF26834A)

private data class Mt5CandleSnapshot(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

private data class Mt5BridgeSnapshot(
    val server: String,
    val bid: Double,
    val ask: Double,
    val candles: Map<String, Mt5CandleSnapshot?>
)

private object Mt5BridgeClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(host: String, token: String): Mt5BridgeSnapshot =
        withContext(Dispatchers.IO) {
            val cleanHost = host.trim()
            val parts = cleanHost.split(".").mapNotNull { it.toIntOrNull() }

            require(
                parts.size == 4 &&
                    parts.all { it in 0..255 } &&
                    (
                        parts[0] == 10 ||
                            (parts[0] == 192 && parts[1] == 168) ||
                            (parts[0] == 172 && parts[1] in 16..31)
                        )
            ) {
                "IP باید آدرس خصوصی شبکه باشد؛ مثل 192.168.1.38"
            }

            require(token.isNotBlank()) { "رمز دسترسی را وارد کن" }

            val request = Request.Builder()
                .url("http://$cleanHost:8765/mt5?bars=3")
                .header("Accept", "application/json")
                .header("X-API-Key", token.trim())
                .get()
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val detail = try {
                        JSONObject(body).optString("error")
                    } catch (_: Exception) {
                        ""
                    }

                    throw IllegalStateException(
                        when (response.code) {
                            401 -> "رمز دسترسی اشتباه است (401)"
                            else -> "خطای ارتباط با MT5: HTTP ${response.code} $detail".trim()
                        }
                    )
                }

                val root = JSONObject(body)
                val tick = root.optJSONObject("tick")
                    ?: error("اطلاعات قیمت در پاسخ MT5 وجود ندارد")
                val candleRoot = root.optJSONObject("candles")
                    ?: error("اطلاعات کندل در پاسخ MT5 وجود ندارد")

                fun latest(name: String): Mt5CandleSnapshot? {
                    val array = candleRoot.optJSONArray(name) ?: return null
                    if (array.length() == 0) return null

                    val item = array.optJSONObject(array.length() - 1) ?: return null

                    return Mt5CandleSnapshot(
                        open = item.optDouble("open", 0.0),
                        high = item.optDouble("high", 0.0),
                        low = item.optDouble("low", 0.0),
                        close = item.optDouble("close", 0.0)
                    )
                }

                Mt5BridgeSnapshot(
                    server = root.optString("server", "MetaTrader 5"),
                    bid = tick.optDouble("bid", 0.0),
                    ask = tick.optDouble("ask", 0.0),
                    candles = mapOf(
                        "M5" to latest("M5"),
                        "M15" to latest("M15"),
                        "M30" to latest("M30")
                    )
                )
            }
        }
}

@Composable
fun Mt5SourceDrawer() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("mt5_bridge_settings", Context.MODE_PRIVATE)
    }

    var expanded by remember { mutableStateOf(true) }
    var host by remember {
        mutableStateOf(
            preferences.getString("host", "192.168.1.38") ?: "192.168.1.38"
        )
    }
    var token by remember {
        mutableStateOf(preferences.getString("token", "") ?: "")
    }
    var connectedMode by remember {
        mutableStateOf(!preferences.getString("token", "").isNullOrBlank())
    }
    var snapshot by remember { mutableStateOf<Mt5BridgeSnapshot?>(null) }
    var candlesExpanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(connectedMode, host, token) {
        if (connectedMode && token.isNotBlank()) {
            while (true) {
                loading = snapshot == null

                try {
                    snapshot = Mt5BridgeClient.fetch(host, token)
                    error = null
                } catch (e: Exception) {
                    error = e.message ?: "ارتباط با پل MT5 برقرار نشد"
                } finally {
                    loading = false
                }

                delay(5_000L)
            }
        }
    }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE9DFCA))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "منبع دوم مستقل: MetaTrader 5",
                        color = Color(0xFF292929),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "قیمت و کندل مستقیم از حساب MT5 ویندوز",
                        color = Mt5Gray,
                        fontSize = 12.sp
                    )
                }

                Text(
                    if (expanded) "⌃" else "⌄",
                    color = Mt5Gold,
                    fontSize = 22.sp
                )
            }

            if (expanded) {
                HorizontalDivider(color = Color(0xFFE9DFCA))

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("IP کامپیوتر") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("رمز دسترسی پل MT5") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                Button(
                    onClick = {
                        preferences.edit()
                            .putString("host", host.trim())
                            .putString("token", token.trim())
                            .apply()

                        snapshot = null
                        error = null
                        connectedMode = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ذخیره تنظیمات و اتصال")
                }

                if (loading && snapshot == null) {
                    CircularProgressIndicator(color = Mt5Gold)
                    Text("در حال اتصال به متاتریدر...")
                }

                snapshot?.let { data ->
                    Text(
                        "وضعیت: متصل",
                        color = Mt5Green,
                        fontWeight = FontWeight.Bold
                    )

                    Text("سرور: ${data.server}", color = Mt5Gray)
                    Text("XAUUSD", color = Mt5Gray, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Bid / خرید", color = Mt5Gray, fontSize = 12.sp)
                            Text(
                                String.format(Locale.US, "%,.2f", data.bid),
                                color = Mt5Red,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text("Ask / فروش", color = Mt5Gray, fontSize = 12.sp)
                            Text(
                                String.format(Locale.US, "%,.2f", data.ask),
                                color = Mt5Green,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        "اختلاف قیمت: ${
                            String.format(Locale.US, "%.2f", data.ask - data.bid)
                        }",
                        color = Mt5Gray
                    )

                    HorizontalDivider(color = Color(0xFFE9DFCA))

                    Button(
    onClick = { candlesExpanded = !candlesExpanded },
    modifier = Modifier.fillMaxWidth()
) {
    Text(
        if (candlesExpanded) "بستن اطلاعات کندل‌ها"
        else "نمایش اطلاعات کندل‌ها"
    )
}

if (candlesExpanded) {
    Text(
        "آخرین کندل‌های دریافتی",
        color = Mt5Gold,
        fontWeight = FontWeight.Bold
    )

    listOf("M5", "M15", "M30").forEach { timeframe ->
        val candle = data.candles[timeframe]

        if (candle != null) {
            Text(
                "$timeframe  |  باز: ${
                    String.format(Locale.US, "%.2f", candle.open)
                }  بیشینه: ${
                    String.format(Locale.US, "%.2f", candle.high)
                }  کمینه: ${
                    String.format(Locale.US, "%.2f", candle.low)
                }  بسته: ${
                    String.format(Locale.US, "%.2f", candle.close)
                }",
                color = Color(0xFF292929),
                fontSize = 12.sp
            )
        } else {
            Text(
                "$timeframe: کندلی دریافت نشد",
                color = Mt5Gray,
                fontSize = 12.sp
            )
        }
    }
}

                error?.let {
                    Text("خطا: $it", color = Mt5Red, fontSize = 13.sp)
                }

                Text(
                    "اتصال فقط از شبکه خصوصی انجام می‌شود. برای دریافت داده، کامپیوتر و MT5 باید روشن و به همان وای‌فای متصل باشند.",
                    color = Mt5Gray,
                    fontSize = 11.sp
                )
            }
        }
    }
}

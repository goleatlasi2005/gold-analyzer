
package com.goldanalyzer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

private val analysisBackground = Color(0xFF171326)
private val analysisCard = Color(0xFF29213D)
private val analysisPurple = Color(0xFFB68CFF)

@Composable
fun AnalysisScreen(symbol: String) {

    val scope = rememberCoroutineScope()
    val candleRepository = remember { CandleRepository() }

    var timeframe by remember { mutableStateOf("1h") }
    var supportTimeframe by remember { mutableStateOf("5m") }

    var chartExpanded by remember { mutableStateOf(false) }
    var supportExpanded by remember { mutableStateOf(false) }

    var chartResults by remember {
        mutableStateOf<List<Pair<String, List<Candle>>>>(emptyList())
    }

    var multiResults by remember {
        mutableStateOf<List<ExchangeMultiTimeframeAnalysis>>(emptyList())
    }

    var supportResults by remember {
        mutableStateOf<List<Pair<String, List<SupportResistanceLevel>>>>(emptyList())
    }

    var loading by remember { mutableStateOf(false) }
    var supportLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadAnalysis() {
        scope.launch {
            loading = true
            errorMessage = null

            try {
                val chartData = coroutineScope {
                    val binance = async {
                        runCatching {
                            candleRepository.getBinanceCandles(
                                symbol, timeframe, 100
                            ).candles
                        }.getOrNull()
                    }

                    val nobitex = async {
                        runCatching {
                            candleRepository.getNobitexCandles(
                                symbol, timeframe, 100
                            ).candles
                        }.getOrNull()
                    }

                    listOfNotNull(
                        binance.await()?.let { "Binance" to it },
                        nobitex.await()?.let { "Nobitex" to it }
                    )
                }

                chartResults = chartData

                if (chartData.isEmpty()) {
                    errorMessage = "اطلاعات نمودار از صرافی‌ها دریافت نشد."
                }

                multiResults =
                    MultiTimeframeAnalysisRepository().analyze(symbol)

            } catch (e: Exception) {
                errorMessage = e.message ?: "خطا در دریافت اطلاعات بازار"
            } finally {
                loading = false
            }
        }
    }

    fun loadSupportResistance() {
        scope.launch {
            supportLoading = true
            supportResults = emptyList()

            try {
                supportResults = coroutineScope {
                    val binance = async {
                        runCatching {
                            candleRepository.getBinanceCandles(
                                symbol, supportTimeframe, 200
                            ).candles
                        }.getOrNull()
                    }

                    val nobitex = async {
                        runCatching {
                            candleRepository.getNobitexCandles(
                                symbol, supportTimeframe, 200
                            ).candles
                        }.getOrNull()
                    }

                    listOfNotNull(
                        binance.await()?.let {
                            "Binance" to SupportResistanceEngine.analyze(it)
                        },
                        nobitex.await()?.let {
                            "Nobitex" to SupportResistanceEngine.analyze(it)
                        }
                    )
                }
            } finally {
                supportLoading = false
            }
        }
    }

    LaunchedEffect(symbol, timeframe) {
        loadAnalysis()
    }

    LaunchedEffect(symbol, supportTimeframe) {
        loadSupportResistance()
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = analysisBackground
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                item {
                    Text(
                        "تحلیل تکنیکال $symbol",
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            loadAnalysis()
                            loadSupportResistance()
                        },
                        enabled = !loading && !supportLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8652D8)
                        )
                    ) {
                        Text("⟳ به‌روزرسانی کل صفحه")
                    }
                }

                // بخش کشویی نمودار
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = analysisCard
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    chartExpanded = !chartExpanded
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "📊 نمودار قیمت",
                                        color = Color.White,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (chartExpanded) "▲" else "▼",
                                        color = analysisPurple,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            if (chartExpanded) {
                                Text(
                                    "انتخاب تایم‌فریم نمودار",
                                    color = analysisPurple,
                                    fontWeight = FontWeight.Bold
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        listOf("5m", "15m", "1h"),
                                        listOf("4h", "1d", "1w")
                                    ).forEach { rowFrames ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            rowFrames.forEach { frame ->
                                                FilterChip(
                                                    selected = timeframe == frame,
                                                    onClick = { timeframe = frame },
                                                    label = { Text(frame) }
                                                )
                                            }
                                        }
                                    }
                                }

                                if (loading) {
                                    CircularProgressIndicator(color = analysisPurple)
                                    Text(
                                        "در حال دریافت اطلاعات بازار...",
                                        color = Color.LightGray
                                    )
                                }

                                Button(
                                    onClick = { loadAnalysis() },
                                    enabled = !loading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF8652D8)
                                    )
                                ) {
                                    Text("⟳ به‌روزرسانی نمودار و تحلیل")
                                }

                                if (errorMessage != null) {
                                    Text(
                                        errorMessage!!,
                                        color = Color(0xFFF87171)
                                    )
                                }

                                chartResults.forEach { chart ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFF211A32)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "${chart.first} - نمودار $timeframe",
                                                color = analysisPurple,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Text(
                                                "تعداد کندل: ${chart.second.size}",
                                                color = Color.LightGray
                                            )

                                            CandlestickChart(candles = chart.second)

                                            chart.second.lastOrNull()?.let { last ->
                                                Text(
                                                    "آخرین قیمت: ${last.close}",
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // بخش کشویی حمایت و مقاومت
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = analysisCard
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    supportExpanded = !supportExpanded
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "📈 حمایت و مقاومت",
                                        color = Color.White,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (supportExpanded) "▲" else "▼",
                                        color = analysisPurple,
                                        fontSize = 18.sp
                                    )
                                }
                            }

                            if (supportExpanded) {
                                Text(
                                    "انتخاب تایم‌فریم سطوح:",
                                    color = analysisPurple
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        listOf("5m", "15m", "1h"),
                                        listOf("4h", "1d", "1w")
                                    ).forEach { rowFrames ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            rowFrames.forEach { frame ->
                                                FilterChip(
                                                    selected = supportTimeframe == frame,
                                                    onClick = {
                                                        supportTimeframe = frame
                                                    },
                                                    label = { Text(frame) }
                                                )
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = { loadSupportResistance() },
                                    enabled = !supportLoading,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF8652D8)
                                    )
                                ) {
                                    Text("⟳ به‌روزرسانی حمایت و مقاومت")
                                }

                                if (supportLoading) {
                                    CircularProgressIndicator(color = analysisPurple)
                                    Text(
                                        "در حال محاسبه حمایت و مقاومت...",
                                        color = Color.LightGray
                                    )
                                }

                                if (!supportLoading && supportResults.isEmpty()) {
                                    Text(
                                        "سطوح در دسترس نیستند یا داده کافی دریافت نشده است.",
                                        color = Color.LightGray
                                    )
                                }

                                supportResults.forEach { exchangeResult ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFF211A32)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                "${exchangeResult.first} - $supportTimeframe",
                                                color = analysisPurple,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            if (exchangeResult.second.isEmpty()) {
                                                Text(
                                                    "سطح قابل محاسبه‌ای با داده فعلی پیدا نشد.",
                                                    color = Color.LightGray
                                                )
                                            }

                                            exchangeResult.second.forEach { level ->
                                                val sideText =
                                                    if (level.side == LevelSide.SUPPORT)
                                                        "حمایت"
                                                    else
                                                        "مقاومت"

                                                val sideColor =
                                                    if (level.side == LevelSide.SUPPORT)
                                                        Color(0xFF4ADE80)
                                                    else
                                                        Color(0xFFF87171)

                                                Text(
                                                    "$sideText | ${level.kind} | قیمت: ${level.price}",
                                                    color = sideColor,
                                                    fontWeight = FontWeight.Bold
                                                )

                                                Text(
                                                    "فاصله: ${"%.2f".format(level.distancePercent)}٪ | قدرت تخمینی: ${level.strength}/100 | برخوردهای محوری: ${level.touches}",
                                                    color = Color.LightGray,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    "سطوح استاتیک از پیوت‌های اخیر و سطوح داینامیک از EMA20، EMA50 و کیجون ایچیموکو محاسبه می‌شوند. قدرت سطوح تخمینی است و واکنش قیمت را تضمین نمی‌کند.",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        "تحلیل ترکیبی چندتایم‌فریمی",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(multiResults) { result ->

                    val signalColor = when (result.finalType) {
                        SignalType.BUY -> Color(0xFF4ADE80)
                        SignalType.SELL -> Color(0xFFF87171)
                        SignalType.HOLD -> Color(0xFFFBBF24)
                    }

                    val signalText = when (result.finalType) {
                        SignalType.BUY -> "خرید"
                        SignalType.SELL -> "فروش"
                        SignalType.HOLD -> "نگهداری"
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = analysisCard
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Text(
                                result.exchange,
                                color = analysisPurple,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "سیگنال نهایی: $signalText",
                                color = signalColor,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "امتیاز ترکیبی: ${result.averageScore} از ۱۰۰",
                                color = Color.White
                            )

                            Text(
                                "توافق تایم‌فریم‌ها: ${result.agreementPercent}٪",
                                color = Color.White
                            )

                            Divider(color = Color.DarkGray)

                            TimeframeSignalTabs(result)

                            val reference = result.referenceSignal

                            if (reference != null) {
                                Divider(color = Color.DarkGray)

                                Text(
                                    "قیمت ورود: ${reference.entryPrice}",
                                    color = Color.White
                                )

                                Text(
                                    "حد ضرر: ${reference.stopLoss}",
                                    color = Color(0xFFF87171)
                                )

                                Text(
                                    "هدف اول: ${reference.takeProfit1}",
                                    color = Color(0xFF4ADE80)
                                )

                                Text(
                                    "هدف دوم: ${reference.takeProfit2}",
                                    color = Color(0xFF4ADE80)
                                )

                                val risk = abs(
                                    reference.entryPrice - reference.stopLoss
                                )

                                if (risk > 0.0) {
                                    val reward1 = abs(
                                        reference.takeProfit1 - reference.entryPrice
                                    )
                                    val reward2 = abs(
                                        reference.takeProfit2 - reference.entryPrice
                                    )

                                    val ratio1 = reward1 / risk
                                    val ratio2 = reward2 / risk

                                    Text(
                                        "نسبت ریسک به بازده هدف اول: ۱ به ${
                                            String.format(Locale.US, "%.2f", ratio1)
                                        }",
                                        color = Color(0xFF4ADE80),
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        "نسبت ریسک به بازده هدف دوم: ۱ به ${
                                            String.format(Locale.US, "%.2f", ratio2)
                                        }",
                                        color = Color(0xFF4ADE80),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "نسبت ریسک به بازده بر اساس فاصله قیمت ورود تا حد ضرر و اهداف محاسبه می‌شود. این نسبت تخمینی است و سودآوری معامله را تضمین نمی‌کند. نمودارها از داده‌های صرافی‌ها رسم می‌شوند و امتیاز یا درصد توافق، احتمال موفقیت معامله نیست.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

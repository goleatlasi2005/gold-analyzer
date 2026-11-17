package com.goldanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val Gold = Color(0xFFD4A52C)
private val DarkGold = Color(0xFF987019)
private val Background = Color(0xFFFFFEFA)
private val TextDark = Color(0xFF292929)
private val TextGray = Color(0xFF777777)
private val Green = Color(0xFF26834A)
private val Red = Color(0xFFB3261E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoldAnalyzerApp()
        }
    }
}

@Composable
fun GoldAnalyzerApp() {
    var selectedMarket by remember {
        mutableStateOf("طلای ۱۸ عیار")
    }

    var selectedTimeframe by remember {
        mutableStateOf("۱۵ دقیقه")
    }

    var quote by remember {
        mutableStateOf<GoldSpotQuote?>(null)
    }

    var ayiarCandles by remember {
        mutableStateOf<CandleSeries?>(null)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    val goldRepository = remember {
        GoldSpotRepository()
    }

    val ayiarRepository = remember {
        AyiarRepository()
    }

    val markets = listOf(
        "طلای ۱۸ عیار",
        "صندوق طلای عیار",
        "انس جهانی طلا"
    )

    val timeframes = listOf(
        "۵ دقیقه", "۱۵ دقیقه", "۳۰ دقیقه",
        "۱ ساعت", "۴ ساعت", "روزانه"
    )

    val intervalMinutes = when (selectedTimeframe) {
        "۵ دقیقه" -> 5
        "۱۵ دقیقه" -> 15
        "۳۰ دقیقه" -> 30
        "۱ ساعت" -> 60
        "۴ ساعت" -> 240
        else -> 0
    }

    LaunchedEffect(selectedMarket, selectedTimeframe) {
        quote = null
        ayiarCandles = null
        errorMessage = null
        loading = false

        when (selectedMarket) {
            "انس جهانی طلا" -> {
                while (true) {
                    loading = quote == null
                    errorMessage = null

                    try {
                        quote = goldRepository.getXauUsdSpot()
                    } catch (e: Exception) {
                        errorMessage =
                            e.message ?: "خطا در دریافت قیمت انس"
                    }

                    loading = false
                    delay(30_000L)
                }
            }

            "صندوق طلای عیار" -> {
                if (intervalMinutes == 0) {
                    errorMessage =
                        "تاریخچه روزانه در مرحله بعد متصل می‌شود."
                } else {
                    while (true) {
                        loading = ayiarCandles == null
                        errorMessage = null

                        try {
                            ayiarCandles =
                                ayiarRepository.getCandles(
                                    date = LocalDate.now(
                                        ZoneId.of("Asia/Tehran")
                                    ),
                                    intervalMinutes = intervalMinutes
                                )
                        } catch (e: Exception) {
                            errorMessage =
                                e.message ?: "خطا در دریافت معاملات عیار"
                        }

                        loading = false
                        delay(30_000L)
                    }
                }
            }

            else -> {
                errorMessage =
                    "منبع قیمت این بازار هنوز متصل نشده است."
            }
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Gold,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🪙", fontSize = 29.sp)
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            "GOLD ANALYZER",
                            color = DarkGold,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "تحلیل هوشمند بازار طلا",
                            color = TextGray,
                            fontSize = 13.sp
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFE9DFCA))

                Text(
                    "انتخاب بازار",
                    color = TextDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                markets.forEach { market ->
                    Card(
                        onClick = {
                            selectedMarket = market
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                if (selectedMarket == market)
                                    Color(0xFFFFF4D6)
                                else Color.White
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selectedMarket == market)
                                Gold else Color(0xFFEAEAEA)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(17.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🪙", fontSize = 25.sp)
                            Spacer(Modifier.width(12.dp))

                            Text(
                                market,
                                color = TextDark,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )

                            RadioButton(
                                selected = selectedMarket == market,
                                onClick = {
                                    selectedMarket = market
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = DarkGold
                                )
                            )
                        }
                    }
                }

                Text(
                    "انتخاب تایم‌فریم",
                    color = TextDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timeframes.chunked(3).forEach { group ->
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(7.dp)
                        ) {
                            group.forEach { frame ->
                                FilterChip(
                                    selected =
                                        selectedTimeframe == frame,
                                    onClick = {
                                        selectedTimeframe = frame
                                    },
                                    label = {
                                        Text(frame)
                                    },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor =
                                                Color(0xFFFFE8A8),
                                            selectedLabelColor = DarkGold
                                        )
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(
                        1.dp,
                        Color(0xFFE9DFCA)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "وضعیت تحلیل",
                            color = DarkGold,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            selectedMarket,
                            color = TextDark,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "تایم‌فریم: $selectedTimeframe",
                            color = TextGray
                        )

                        HorizontalDivider(
                            color = Color(0xFFEAEAEA)
                        )

                        when (selectedMarket) {
                            "انس جهانی طلا" -> {
                                when {
                                    loading -> {
                                        CircularProgressIndicator(
                                            color = DarkGold
                                        )
                                        Text("در حال دریافت قیمت انس...")
                                    }

                                    quote != null -> {
                                        Text(
                                            "قیمت هر اونس به دلار",
                                            color = TextGray
                                        )

                                        Text(
                                            String.format(
                                                Locale.US,
                                                "%,.2f USD",
                                                quote!!.priceUsdPerTroyOunce
                                            ),
                                            color = DarkGold,
                                            fontSize = 27.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Text(
                                            "منبع: ${quote!!.source}",
                                            color = TextGray
                                        )

                                        Text(
                                            "زمان قیمت: " +
                                                formatTehranTime(
                                                    quote!!.observedAtMillis
                                                ),
                                            color = TextGray
                                        )

                                        Text(
                                            if (quote!!.isFresh)
                                                "داده تازه"
                                            else
                                                "هشدار: داده تازه نیست؛ سیگنال صادر نمی‌شود",
                                            color = if (quote!!.isFresh)
                                                Green else Red
                                        )
                                    }
                                }
                            }

                            "صندوق طلای عیار" -> {
                                if (intervalMinutes == 0) {
                                    Text(
                                        "تاریخچه روزانه عیار هنوز متصل نشده است.",
                                        color = TextGray
                                    )
                                } else {
                                    when {
                                        loading -> {
                                            CircularProgressIndicator(
                                                color = DarkGold
                                            )
                                            Text(
                                                "در حال دریافت معاملات عیار..."
                                            )
                                        }

                                        ayiarCandles != null -> {
                                            val candles =
                                                ayiarCandles!!.candles

                                            val latest =
                                                candles.last()

                                            Text(
                                                "قیمت پایانی آخرین کندل",
                                                color = TextGray
                                            )

                                            Text(
                                                String.format(
                                                    Locale.US,
                                                    "%,.0f ریال",
                                                    latest.close
                                                ),
                                                color = DarkGold,
                                                fontSize = 27.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            HorizontalDivider(
                                                color = Color(0xFFEAEAEA)
                                            )

                                            PriceRow(
                                                "بازشدن",
                                                latest.open
                                            )
                                            PriceRow(
                                                "بالاترین",
                                                latest.high
                                            )
                                            PriceRow(
                                                "پایین‌ترین",
                                                latest.low
                                            )
                                            PriceRow(
                                                "پایانی",
                                                latest.close
                                            )

                                            Text(
                                                "حجم معاملات کندل: " +
                                                    String.format(
                                                        Locale.US,
                                                        "%,.0f",
                                                        latest.volume
                                                    ),
                                                color = TextGray
                                            )

                                            Text(
                                                "تعداد کندل‌های دریافتی: ${candles.size}",
                                                color = TextGray
                                            )

                                            Text(
                                                "زمان آخرین کندل: " +
                                                    formatTehranTime(
                                                        latest.closeTime
                                                    ),
                                                color = TextGray
                                            )

                                            Text(
                                                "منبع: TSETMC",
                                                color = TextGray
                                            )
                                        }
                                    }
                                }
                            }

                            else -> {
                                Text(
                                    "منبع قیمت این بازار هنوز متصل نشده است.",
                                    color = TextGray,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        if (errorMessage != null) {
                            Text(
                                "پیام: $errorMessage",
                                color = Red,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            "سیگنال معاملاتی تا زمان تأیید کیفیت و تازگی داده صادر نمی‌شود.",
                            color = TextGray,
                            fontSize = 13.sp
                        )
                    }
                }

                Text(
                    "ابزارهای تحلیل تکنیکال",
                    color = TextDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "EMA • RSI • MACD • Ichimoku • Fibonacci • ATR • ADX",
                    color = DarkGold,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PriceRow(
    title: String,
    value: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            color = TextGray
        )

        Text(
            String.format(
                Locale.US,
                "%,.0f ریال",
                value
            ),
            color = TextDark,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTehranTime(
    timestamp: Long
): String {
    val formatter = SimpleDateFormat(
        "HH:mm:ss",
        Locale.getDefault()
    )
    formatter.timeZone =
        TimeZone.getTimeZone("Asia/Tehran")

    return formatter.format(Date(timestamp))
}


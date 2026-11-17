package com.goldanalyzer

import android.os.Bundle
import android.icu.util.Calendar
import android.icu.util.ULocale
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
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

private val TehranZone = ZoneId.of("Asia/Tehran")

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

    var ayiarDailyCandles by remember {
        mutableStateOf<CandleSeries?>(null)
    }

    var loading by remember {
        mutableStateOf(false)
    }

    var refreshingAll by remember {
        mutableStateOf(false)
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    var refreshStatus by remember {
        mutableStateOf<String?>(null)
    }

    val goldRepository = remember {
        GoldSpotRepository()
    }

    val ayiarRepository = remember {
        AyiarRepository()
    }

    val refreshMutex = remember {
        Mutex()
    }

    val scope = rememberCoroutineScope()

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

    suspend fun fetchAyiarData(): CandleSeries? {
        if (ayiarDailyCandles == null) {
            ayiarDailyCandles =
                ayiarRepository.getDailyCandles(500)
        }

        return if (intervalMinutes == 0) {
            ayiarDailyCandles
        } else {
            ayiarRepository.getCandles(
                date = LocalDate.now(TehranZone),
                intervalMinutes = intervalMinutes
            )
        }
    }

    fun getAyiarPercentChange(): Double? {
        val currentSeries = ayiarCandles ?: return null
        val candles = currentSeries.candles
        val latest = candles.lastOrNull() ?: return null

        val latestDate = Instant.ofEpochMilli(latest.openTime)
            .atZone(TehranZone)
            .toLocalDate()

        val previousClose = ayiarDailyCandles
            ?.candles
            ?.filter { candle ->
                Instant.ofEpochMilli(candle.openTime)
                    .atZone(TehranZone)
                    .toLocalDate()
                    .isBefore(latestDate)
            }
            ?.lastOrNull()
            ?.close

        return calculatePercentChange(
            latest.close,
            previousClose
        )
    }

    LaunchedEffect(selectedMarket, selectedTimeframe) {
        quote = null
        ayiarCandles = null
        errorMessage = null
        refreshStatus = null
        loading = false

        when (selectedMarket) {
            "انس جهانی طلا" -> {
                while (true) {
                    try {
                        refreshMutex.withLock {
                            loading = quote == null
                            quote = goldRepository.getXauUsdSpot()
                            loading = false
                        }
                        errorMessage = null
                    } catch (e: Exception) {
                        loading = false
                        errorMessage =
                            e.message ?: "خطا در دریافت قیمت انس"
                    }

                    delay(60_000L)
                }
            }

            "صندوق طلای عیار" -> {
                while (true) {
                    if (isAyiarMarketOpen()) {
                        try {
                            refreshMutex.withLock {
                                loading = ayiarCandles == null
                                ayiarCandles = fetchAyiarData()
                                loading = false
                            }
                            errorMessage = null
                        } catch (e: Exception) {
                            loading = false
                            errorMessage =
                                e.message ?: "خطا در دریافت اطلاعات عیار"
                        }

                        delay(10_000L)
                    } else {
                        delay(60_000L)
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
                                    selected = selectedTimeframe == frame,
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

                Button(
                    onClick = {
                        if (!refreshingAll) {
                            scope.launch {
                                refreshingAll = true
                                refreshStatus = null

                                var spotResult = false
                                var ayiarResult = false
                                var spotError: String? = null
                                var ayiarError: String? = null

                                try {
                                    refreshMutex.withLock {
                                        try {
                                            quote =
                                                goldRepository.getXauUsdSpot()
                                            spotResult = true
                                        } catch (e: Exception) {
                                            spotError = e.message
                                        }

                                        try {
                                            ayiarCandles = fetchAyiarData()
                                            ayiarResult =
                                                ayiarCandles != null
                                        } catch (e: Exception) {
                                            ayiarError = e.message
                                        }
                                    }
                                } catch (e: Exception) {
                                    refreshStatus =
                                        "خطا در به‌روزرسانی اطلاعات"
                                }

                                val messages = mutableListOf<String>()

                                messages.add(
                                    if (spotResult)
                                        "انس: دریافت شد"
                                    else
                                        "انس: ${spotError ?: "دریافت نشد"}"
                                )

                                messages.add(
                                    if (ayiarResult)
                                        "عیار: دریافت شد"
                                    else
                                        "عیار: ${ayiarError ?: "داده‌ای دریافت نشد"}"
                                )

                                messages.add(
                                    "طلای ۱۸ عیار: منبع هنوز متصل نیست"
                                )

                                refreshStatus = messages.joinToString("\n")
                                refreshingAll = false
                            }
                        }
                    },
                    enabled = !refreshingAll,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGold,
                        disabledContainerColor = Color(0xFFBDAA7B)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (refreshingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("در حال به‌روزرسانی همه بازارها...")
                    } else {
                        Text(
                            "↻  به‌روزرسانی همه بازارها",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (refreshStatus != null) {
                    Text(
                        refreshStatus!!,
                        color = TextGray,
                        fontSize = 13.sp
                    )
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

                                        ChangePercentRow(
                                            calculatePercentChange(
                                                quote!!.priceUsdPerTroyOunce,
                                                quote!!.previousDailyCloseUsd
                                            )
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
                                when {
                                    loading -> {
                                        CircularProgressIndicator(
                                            color = DarkGold
                                        )
                                        Text(
                                            "در حال دریافت کندل‌های عیار..."
                                        )
                                    }

                                    ayiarCandles != null -> {
                                        val candles =
                                            ayiarCandles!!.candles

                                        if (candles.isNotEmpty()) {
                                            val latest = candles.last()

                                            Text(
                                                if (intervalMinutes == 0)
                                                    "قیمت پایانی آخرین کندل روزانه"
                                                else
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

                                            ChangePercentRow(
                                                getAyiarPercentChange()
                                            )

                                            HorizontalDivider(
                                                color = Color(0xFFEAEAEA)
                                            )

                                            PriceRow("بازشدن", latest.open)
                                            PriceRow("بالاترین", latest.high)
                                            PriceRow("پایین‌ترین", latest.low)
                                            PriceRow("پایانی", latest.close)

                                            Text(
                                                "حجم معاملات: " +
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
                                                "شروع آخرین کندل: " +
                                                    formatTehranTime(
                                                        latest.openTime
                                                    ),
                                                color = TextGray
                                            )

                                            Text(
                                                "منبع: TSETMC",
                                                color = TextGray
                                            )

                                            HorizontalDivider(
                                                color = Color(0xFFEAEAEA)
                                            )

                                            CandlestickChart(
                                                candles = candles,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            Text(
                                                "در حال حاضر کندلی برای نمایش دریافت نشد.",
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
                                ChangePercentRow(null)
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

private fun calculatePercentChange(
    current: Double,
    previous: Double?
): Double? {
    if (previous == null || previous <= 0.0) {
        return null
    }

    return ((current - previous) / previous) * 100.0
}

@Composable
private fun ChangePercentRow(percent: Double?) {
    val color = when {
        percent == null -> TextGray
        percent > 0.0 -> Green
        percent < 0.0 -> Red
        else -> TextGray
    }

    val text = percent?.let {
        "تغییر نسبت به روز قبل: " +
            String.format(Locale.US, "%+.2f%%", it)
    } ?: "درصد تغییر روزانه در دسترس نیست"

    Text(
        text = text,
        color = color,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    )
}

private fun isAyiarMarketOpen(): Boolean {
    val tehranNow = ZonedDateTime.now(TehranZone)

    val day = tehranNow.dayOfWeek
    val hour = tehranNow.hour

    val isTradingDay =
        day != DayOfWeek.THURSDAY &&
        day != DayOfWeek.FRIDAY

    return isTradingDay && hour >= 12 && hour < 18
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

private fun formatTehranTime(timestamp: Long): String {
    val timeZone = TimeZone.getTimeZone("Asia/Tehran")

    val calendar = Calendar.getInstance(
        android.icu.util.TimeZone.getTimeZone("Asia/Tehran"),
        ULocale("fa_IR@calendar=persian")
    )

    calendar.setTimeInMillis(timestamp)

    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DATE)

    val timeFormatter = SimpleDateFormat(
        "HH:mm:ss",
        Locale.US
    )

    timeFormatter.timeZone = timeZone

    val date = String.format(
        Locale.US,
        "%04d/%02d/%02d",
        year,
        month,
        day
    )

    val time = timeFormatter.format(Date(timestamp))

    return "$date $time"
}


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

    var spotQuote by remember {
        mutableStateOf<GoldSpotQuote?>(null)
    }

    var ayiarQuote by remember {
        mutableStateOf<AyiarQuote?>(null)
    }

    var ayiarCandles by remember {
        mutableStateOf<CandleSeries?>(null)
    }

    var ayiarDailyCandles by remember {
        mutableStateOf<CandleSeries?>(null)
    }

    var priceLoading by remember { mutableStateOf(false) }
    var chartLoading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    var priceError by remember { mutableStateOf<String?>(null) }
    var chartError by remember { mutableStateOf<String?>(null) }
    var refreshStatus by remember { mutableStateOf<String?>(null) }

    val goldRepository = remember { GoldSpotRepository() }
    val ayiarRepository = remember { AyiarRepository() }
    val refreshMutex = remember { Mutex() }
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

    // تنظیم حداقل کندل و میانگین بلندمدت متناسب با تایم‌فریم
    val isHourlyTimeframe =
        selectedTimeframe == "۱ ساعت" ||
        selectedTimeframe == "۴ ساعت"

    val requiredCandles = if (isHourlyTimeframe) 130 else 200
    val longEmaPeriod = if (isHourlyTimeframe) 100 else 200
val selectedAyiarSeries = if (intervalMinutes == 0) {
    ayiarDailyCandles?.takeIf { it.interval == "1d" }
} else {
    ayiarCandles?.takeIf {
        it.interval == "${intervalMinutes}m"
    }
}
    suspend fun loadAyiarPrice() {
        ayiarQuote = ayiarRepository.getQuote()
    }

    suspend fun loadAyiarChart() {
        if (ayiarCandles?.candles.isNullOrEmpty()) {
            chartLoading = true
        }

        chartError = null

        try {
            val newCandles = if (intervalMinutes == 0) {
                if (ayiarDailyCandles == null) {
                    ayiarDailyCandles =
                        ayiarRepository.getDailyCandles(500)
                }
                ayiarDailyCandles
            } else {
                ayiarRepository.getCandles(
                    date = LocalDate.now(TehranZone),
                    intervalMinutes = intervalMinutes
                )
            }

            if (newCandles?.candles.isNullOrEmpty()) {
                chartError = "کندلی برای نمایش دریافت نشد."
            } else {
                ayiarCandles = newCandles
            }
        } catch (e: Exception) {
            chartError = e.message ?: "خطا در دریافت نمودار"
        } finally {
            chartLoading = false
        }
    }

    suspend fun refreshSelectedMarket() {
        refreshMutex.withLock {
            priceError = null
            chartError = null
            refreshStatus = null

            when (selectedMarket) {
                "انس جهانی طلا" -> {
                    priceLoading = true
                    try {
                        spotQuote = goldRepository.getXauUsdSpot()
                    } catch (e: Exception) {
                        priceError =
                            e.message ?: "خطا در دریافت قیمت انس"
                    } finally {
                        priceLoading = false
                    }
                }

                "صندوق طلای عیار" -> {
                    priceLoading = true
                    try {
                        loadAyiarPrice()
                    } catch (e: Exception) {
                        priceError =
                            e.message ?: "خطا در دریافت قیمت عیار"
                    } finally {
                        priceLoading = false
                    }

                    loadAyiarChart()
                }

                else -> {
                    priceError =
                        "منبع قیمت طلای ۱۸ عیار هنوز متصل نشده است."
                }
            }
        }
    }

    // به‌روزرسانی قیمت مستقل از نمودار
    LaunchedEffect(selectedMarket) {
        priceError = null

        when (selectedMarket) {
            "انس جهانی طلا" -> {
                while (true) {
                    refreshMutex.withLock {
                        priceLoading = spotQuote == null
                        try {
                            spotQuote = goldRepository.getXauUsdSpot()
                            priceError = null
                        } catch (e: Exception) {
                            priceError =
                                e.message ?: "خطا در دریافت قیمت انس"
                        } finally {
                            priceLoading = false
                        }
                    }
                    delay(60_000L)
                }
            }

            "صندوق طلای عیار" -> {
                while (true) {
                    if (isAyiarMarketOpen()) {
                        refreshMutex.withLock {
                            priceLoading = ayiarQuote == null
                            try {
                                loadAyiarPrice()
                                priceError = null
                            } catch (e: Exception) {
                                priceError =
                                    e.message ?: "خطا در دریافت قیمت عیار"
                            } finally {
                                priceLoading = false
                            }
                        }
                        delay(10_000L)
                    } else {
                        delay(60_000L)
                    }
                }
            }

            else -> {
                priceError =
                    "منبع قیمت این بازار هنوز متصل نشده است."
            }
        }
    }

    // به‌روزرسانی نمودار متناسب با تایم‌فریم
    LaunchedEffect(selectedMarket, selectedTimeframe) {
        chartError = null

        if (selectedMarket == "صندوق طلای عیار") {
            refreshMutex.withLock {
                loadAyiarChart()
            }

            while (true) {
                if (isAyiarMarketOpen()) {
                    delay(chartRefreshIntervalMillis(intervalMinutes))

                    if (isAyiarMarketOpen()) {
                        refreshMutex.withLock {
                            loadAyiarChart()
                        }
                    }
                } else {
                    delay(60_000L)
                }
            }
        }
    }

    val ayiarPercent = ayiarQuote?.let {
        calculatePercentChange(it.currentPrice, it.previousClose)
    }

    // فقط کندل‌های کامل وارد تحلیل می‌شوند.
    val completedCandles = remember(selectedAyiarSeries) {
    val now = System.currentTimeMillis()
    selectedAyiarSeries?.candles.orEmpty()
        .filter { it.closeTime <= now }
        .distinctBy { it.openTime }
        .sortedBy { it.openTime }
}

    // محاسبه سیگنال با حداقل کندل و EMA متناسب با تایم‌فریم
    val tradingSignal = remember(
        ayiarCandles,
        selectedMarket,
        selectedTimeframe
    ) {
        if (selectedMarket == "صندوق طلای عیار") {
            SignalEngine.analyze(
                candles = completedCandles,
                minimumCandles = requiredCandles,
                longEmaPeriod = longEmaPeriod
            )
        } else {
            null
        }
    }

    // محاسبه حمایت و مقاومت استاتیک و داینامیک
    val supportResistanceLevels = remember(
        ayiarCandles,
        selectedMarket
    ) {
        if (selectedMarket == "صندوق طلای عیار") {
            SupportResistanceEngine.analyze(completedCandles)
        } else {
            emptyList()
        }
    }

    val lastAnalyzedCandle = completedCandles.lastOrNull()

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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        onClick = { selectedMarket = market },
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
                            modifier = Modifier.padding(15.dp),
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
                                onClick = { selectedMarket = market },
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

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    timeframes.chunked(3).forEach { group ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            group.forEach { frame ->
                                FilterChip(
                                    selected = selectedTimeframe == frame,
                                    onClick = { selectedTimeframe = frame },
                                    label = { Text(frame) },
                                    colors = FilterChipDefaults.filterChipColors(
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
                        if (!refreshing) {
                            scope.launch {
                                refreshing = true
                                refreshStatus = null
                                try {
                                    refreshSelectedMarket()
                                    refreshStatus =
                                        "به‌روزرسانی $selectedMarket انجام شد."
                                } finally {
                                    refreshing = false
                                }
                            }
                        }
                    },
                    enabled = !refreshing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DarkGold
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("در حال به‌روزرسانی...")
                    } else {
                        Text(
                            "↻  به‌روزرسانی $selectedMarket",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (refreshStatus != null) {
                    Text(refreshStatus!!, color = Green, fontSize = 13.sp)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color(0xFFE9DFCA))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "قیمت بازار انتخاب‌شده",
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

                        HorizontalDivider(color = Color(0xFFEAEAEA))

                        when (selectedMarket) {
                            "انس جهانی طلا" -> {
                                if (priceLoading && spotQuote == null) {
                                    CircularProgressIndicator(color = DarkGold)
                                    Text("در حال دریافت قیمت...")
                                }

                                spotQuote?.let { q ->
                                    Text(
                                        "قیمت هر اونس به دلار",
                                        color = TextGray
                                    )
                                    Text(
                                        String.format(
                                            Locale.US,
                                            "%,.2f USD",
                                            q.priceUsdPerTroyOunce
                                        ),
                                        color = DarkGold,
                                        fontSize = 27.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ChangePercentRow(
                                        calculatePercentChange(
                                            q.priceUsdPerTroyOunce,
                                            q.previousDailyCloseUsd
                                        )
                                    )
                                    Text("منبع: ${q.source}", color = TextGray)
                                    Text(
                                        "زمان قیمت: ${formatTehranTime(q.observedAtMillis)}",
                                        color = TextGray
                                    )
                                    Text(
                                        if (q.isFresh) "داده تازه"
                                        else "هشدار: داده تازه نیست",
                                        color = if (q.isFresh) Green else Red
                                    )
                                }
                            }

                            "صندوق طلای عیار" -> {
                                if (priceLoading && ayiarQuote == null) {
                                    CircularProgressIndicator(color = DarkGold)
                                    Text("در حال دریافت قیمت...")
                                }

                                ayiarQuote?.let { q ->
                                    Text(
                                        "قیمت آخرین معامله",
                                        color = TextGray
                                    )
                                    Text(
                                        String.format(
                                            Locale.US,
                                            "%,.0f ریال",
                                            q.currentPrice
                                        ),
                                        color = DarkGold,
                                        fontSize = 27.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    ChangePercentRow(ayiarPercent)
                                    Text(
                                        "قیمت پایانی: " +
                                            String.format(
                                                Locale.US,
                                                "%,.0f ریال",
                                                q.closingPrice
                                            ),
                                        color = TextGray
                                    )
                                    Text(
                                        "منبع: TSETMC",
                                        color = TextGray
                                    )
                                    Text(
                                        "زمان قیمت: ${formatAyiarDateTime(q.date, q.time)}",
                                        color = TextGray
                                    )
                                }

                                if (ayiarQuote == null && !priceLoading) {
                                    Text(
                                        "قیمت عیار دریافت نشده است.",
                                        color = TextGray
                                    )
                                }
                            }

                            else -> {
                                Text(
                                    "منبع قیمت این بازار هنوز متصل نشده است.",
                                    color = TextGray
                                )
                            }
                        }

                        if (priceError != null) {
                            Text(
                                "پیام: $priceError",
                                color = Red,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                ExpandableSection("نمودار قیمت") {
                    when (selectedMarket) {
                        "صندوق طلای عیار" -> {
                            val candles = ayiarCandles?.candles.orEmpty()

                            if (candles.isNotEmpty()) {
                                Text(
                                    "تعداد کندل‌ها: ${candles.size}",
                                    color = TextGray
                                )
                                CandlestickChart(
                                    candles = candles,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (chartError != null) {
                                    Text(
                                        "به‌روزرسانی نمودار ناموفق بود؛ " +
                                            "نمودار قبلی نمایش داده می‌شود.",
                                        color = TextGray
                                    )
                                }
                            } else if (chartLoading) {
                                CircularProgressIndicator(color = DarkGold)
                                Text("در حال دریافت نمودار...")
                            } else {
                                Text(
                                    chartError
                                        ?: "نموداری برای نمایش موجود نیست.",
                                    color = TextGray
                                )
                            }
                        }

                        "انس جهانی طلا" -> {
                            Text(
                                "اتصال تاریخچه نمودار انس هنوز انجام نشده است.",
                                color = TextGray
                            )
                        }

                        else -> {
                            Text(
                                "پس از اتصال منبع قیمت طلای ۱۸ عیار، نمودار این بازار فعال می‌شود.",
                                color = TextGray
                            )
                        }
                    }
                }

                ExpandableSection("سیگنال معاملاتی") {
                    when (selectedMarket) {
                        "صندوق طلای عیار" -> {
                            when {
                                chartLoading && completedCandles.isEmpty() -> {
                                    CircularProgressIndicator(color = DarkGold)
                                    Text("در حال دریافت کندل‌ها برای تحلیل...")
                                }

                                completedCandles.size < requiredCandles -> {
                                    Text(
                                        "برای محاسبه سیگنال در این تایم‌فریم حداقل $requiredCandles کندل کامل لازم است.",
                                        color = TextGray
                                    )
                                    Text(
                                        "تعداد کندل کامل فعلی: ${completedCandles.size}",
                                        color = DarkGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "در این تایم‌فریم فعلاً سیگنال صادر نمی‌شود.",
                                        color = Red
                                    )
                                }

                                tradingSignal == null -> {
                                    Text(
                                        "داده‌های دریافت‌شده برای محاسبه اندیکاتورها کافی یا معتبر نیستند.",
                                        color = Red
                                    )
                                }

                                else -> {
                                    TradingSignalContent(
                                        signal = tradingSignal,
                                        candleCount = completedCandles.size,
                                        candleTime = lastAnalyzedCandle?.openTime,
                                        oneWayMarket = selectedMarket == "صندوق طلای عیار"
                                    )
                                }
                            }
                        }

                        else -> {
                            Text(
                                "موتور تحلیل این بازار هنوز به منبع کندل معتبر متصل نشده است.",
                                color = TextGray
                            )
                        }
                    }
                }
CombinedSignalsSection(
    repository = ayiarRepository,
    enabled = selectedMarket == "صندوق طلای عیار",
    oneWayMarket = true,
    selectedTimeframe = selectedTimeframe,
    selectedSeries = selectedAyiarSeries
)
                ExpandableSection("تحلیل تکنیکال") {
                    if (selectedMarket == "صندوق طلای عیار") {
                        Text(
                            "تایم‌فریم: $selectedTimeframe",
                            color = DarkGold,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "تعداد کندل‌های کامل: ${completedCandles.size}",
                            color = TextGray
                        )

                        Text(
                            "حداقل کندل موردنیاز: $requiredCandles",
                            color = TextGray
                        )

                        Text(
                            "میانگین بلندمدت: EMA$longEmaPeriod",
                            color = TextGray
                        )

                        if (tradingSignal != null) {
                            Text(
                                "جهت روند: ${tradingSignal.trend}",
                                color = TextDark,
                                fontWeight = FontWeight.Bold
                            )

                            tradingSignal.adx?.let {
                                Text(
                                    "ADX: ${String.format(Locale.US, "%.2f", it)}",
                                    color = TextDark
                                )
                            }

                            Text(
                                "ATR: ${formatRial(tradingSignal.atr)}",
                                color = TextDark
                            )

                            HorizontalDivider(color = Color(0xFFEAEAEA))

                            Text(
                                "دلایل تحلیل:",
                                color = DarkGold,
                                fontWeight = FontWeight.Bold
                            )

                            tradingSignal.reasons.forEach { reason ->
                                Text(
                                    "• $reason",
                                    color = TextGray
                                )
                            }
                        } else {
                            Text(
                                "پس از دریافت حداقل $requiredCandles کندل کامل، تحلیل اندیکاتورها نمایش داده می‌شود.",
                                color = TextGray
                            )
                        }
                    } else {
                        Text(
                            "تحلیل مستقل این بازار پس از اتصال منبع کندل معتبر فعال می‌شود.",
                            color = TextGray
                        )
                    }
                }

                // کشوی مستقل حمایت و مقاومت
                ExpandableSection("حمایت و مقاومت") {
                    if (selectedMarket != "صندوق طلای عیار") {
                        Text(
                            "این بخش فعلاً برای صندوق طلای عیار فعال است.",
                            color = TextGray
                        )
                    } else if (completedCandles.size < 60) {
                        Text(
                            "برای محاسبه حمایت و مقاومت حداقل ۶۰ کندل کامل لازم است.",
                            color = TextGray
                        )
                        Text(
                            "تعداد کندل کامل فعلی: ${completedCandles.size}",
                            color = DarkGold
                        )
                    } else if (supportResistanceLevels.isEmpty()) {
                        Text(
                            "در داده‌های فعلی سطح معتبر حمایت یا مقاومت پیدا نشد.",
                            color = TextGray
                        )
                    } else {
                        Text(
                            "قیمت مبنا: ${formatRial(completedCandles.last().close)}",
                            color = TextDark,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider(color = Color(0xFFEAEAEA))

                        Text(
                            "سطوح استاتیک",
                            color = DarkGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        val staticLevels =
                            supportResistanceLevels.filter {
                                it.kind == "استاتیک"
                            }

                        if (staticLevels.isEmpty()) {
                            Text(
                                "سطح استاتیک نزدیک و معتبری پیدا نشد.",
                                color = TextGray
                            )
                        } else {
                            staticLevels.forEach { level ->
                                SupportResistanceRow(level)
                            }
                        }

                        HorizontalDivider(color = Color(0xFFEAEAEA))

                        Text(
                            "سطوح داینامیک",
                            color = DarkGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        val dynamicLevels =
                            supportResistanceLevels.filter {
                                it.kind == "داینامیک"
                            }

                        if (dynamicLevels.isEmpty()) {
                            Text(
                                "سطح داینامیک در دسترس نیست.",
                                color = TextGray
                            )
                        } else {
                            dynamicLevels.forEach { level ->
                                SupportResistanceRow(level)
                            }
                        }

                        Text(
                            "سطوح استاتیک از سقف‌ها و کف‌های چرخشی قیمت " +
                                "و سطوح داینامیک از EMA20، EMA50 و کیجون ایچیموکو محاسبه می‌شوند.",
                            color = TextGray,
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    "هشدار: سیگنال‌ها خروجی محاسبات تکنیکال هستند و احتمال سود یا زیان را تضمین نمی‌کنند. این نسخه هنوز بک‌تست عملکرد ندارد.",
                    color = TextGray,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SupportResistanceRow(
    level: SupportResistanceLevel
) {
    val isSupport = level.side == LevelSide.SUPPORT
    val levelColor = if (isSupport) Green else Red
    val sideText = if (isSupport) "حمایت" else "مقاومت"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSupport)
                Color(0xFFF1F8F2)
            else
                Color(0xFFFFF3F1)
        )
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    sideText,
                    color = levelColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    level.kind,
                    color = TextGray,
                    fontSize = 12.sp
                )
            }

            Text(
                formatRial(level.price),
                color = TextDark,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "فاصله: ${String.format(Locale.US, "%.2f", level.distancePercent)}٪",
                    color = TextGray,
                    fontSize = 12.sp
                )

                if (level.touches > 0) {
                    Text(
                        "تعداد برخورد: ${level.touches}",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }

                Text(
                    "قدرت: ${level.strength}٪",
                    color = levelColor,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun TradingSignalContent(
    signal: TradingSignal,
    candleCount: Int,
    candleTime: Long?,
    oneWayMarket: Boolean = false
) {
    val signalColor = when (signal.type) {
        SignalType.BUY -> Green
        SignalType.SELL -> Red
        SignalType.HOLD -> DarkGold
    }

    val signalText = when (signal.type) {
        SignalType.BUY -> "خرید"
        SignalType.SELL -> if (oneWayMarket) "فروش واحدهای موجود" else "فروش"
        SignalType.HOLD -> "صبر"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFFEFA)
        ),
        border = BorderStroke(1.dp, signalColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                "وضعیت سیگنال: $signalText",
                color = signalColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "قدرت امتیاز: ${signal.score} از ۱۰۰",
                color = TextDark,
                fontWeight = FontWeight.Bold
            )

            Text(
                "شاخص اطمینان محاسباتی: ${signal.confidencePercent}٪",
                color = TextGray
            )

            Text(
                "روند: ${signal.trend}",
                color = TextDark
            )

            Text(
                "تعداد کندل‌های بررسی‌شده: $candleCount",
                color = TextGray
            )

            candleTime?.let {
                Text(
                    "زمان آخرین کندل تحلیل‌شده: ${formatTehranTime(it)}",
                    color = TextGray,
                    fontSize = 12.sp
                )
            }

            HorizontalDivider(color = Color(0xFFEAEAEA))

            if (oneWayMarket && signal.type == SignalType.SELL) {
                Text(
                    "قیمت مرجع فروش: ${formatRial(signal.entryPrice)}",
                    color = TextDark,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "این سیگنال فقط برای فروش واحدهایی است که از قبل در اختیار داری؛ فروش استقراضی در صندوق عیار امکان‌پذیر نیست.",
                    color = Red,
                    fontSize = 12.sp
                )
            } else {
                Text(
                    "قیمت مرجع ورود: ${formatRial(signal.entryPrice)}",
                    color = TextDark,
                    fontWeight = FontWeight.Bold
                )
            }

            if (signal.type == SignalType.BUY) {
                Text(
                    "حد ضرر: ${formatRial(signal.stopLoss)}",
                    color = Red
                )

                Text(
                    "حد سود اول: ${formatRial(signal.takeProfit1)}",
                    color = Green
                )

                Text(
                    "حد سود دوم: ${formatRial(signal.takeProfit2)}",
                    color = Green
                )

                Text(
                    "حد سود سوم: ${formatRial(signal.takeProfit3)}",
                    color = Green
                )

                Text(
                    "نسبت ریسک به بازده هدف: ۱ به ${String.format(Locale.US, "%.1f", signal.riskRewardRatio)}",
                    color = DarkGold,
                    fontWeight = FontWeight.Bold
                )
            } else if (signal.type == SignalType.HOLD) {
                Text(
                    "شرایط ورود کامل نیست؛ فعلاً معامله‌ای پیشنهاد نمی‌شود.",
                    color = DarkGold
                )
            }

            HorizontalDivider(color = Color(0xFFEAEAEA))

            Text(
                "دلایل صدور سیگنال:",
                color = DarkGold,
                fontWeight = FontWeight.Bold
            )

            signal.reasons.forEach { reason ->
                Text(
                    "• $reason",
                    color = TextGray,
                    fontSize = 13.sp
                )
            }

            Text(
                "این اطمینان، امتیاز الگوریتم است و احتمال آماری موفقیت معامله نیست.",
                color = TextGray,
                fontSize = 11.sp
            )
        }
    }
}

private fun formatRial(value: Double): String {
    return String.format(Locale.US, "%,.0f ریال", value)
}

@Composable
private fun ExpandableSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(1.dp, Color(0xFFE9DFCA))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = DarkGold,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) "بستن ▲" else "نمایش ▼",
                        color = DarkGold
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(
                    color = Color(0xFFEAEAEA),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

private fun chartRefreshIntervalMillis(intervalMinutes: Int): Long {
    return if (intervalMinutes <= 0) {
        24 * 60 * 60 * 1000L
    } else {
        intervalMinutes * 60 * 1000L
    }
}

private fun calculatePercentChange(
    current: Double,
    previous: Double?
): Double? {
    if (previous == null || previous <= 0.0) return null
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
    val now = ZonedDateTime.now(TehranZone)
    val day = now.dayOfWeek
    val isTradingDay =
        day != DayOfWeek.THURSDAY &&
        day != DayOfWeek.FRIDAY

    return isTradingDay && now.hour >= 12 && now.hour < 18
}

private fun formatAyiarDateTime(
    dateValue: Long,
    timeValue: Long
): String {
    val dateText = dateValue.toString().padStart(8, '0')

    val date = LocalDate.of(
        dateText.substring(0, 4).toInt(),
        dateText.substring(4, 6).toInt(),
        dateText.substring(6, 8).toInt()
    )

    val dateTimestamp = date
        .atStartOfDay(TehranZone)
        .toInstant()
        .toEpochMilli()

    val datePart = formatTehranTime(dateTimestamp)
        .substringBefore(" ")

    val hour = (timeValue / 10000).toInt()
    val minute = ((timeValue / 100) % 100).toInt()
    val second = (timeValue % 100).toInt()

    val timePart = if (
        hour in 0..23 &&
        minute in 0..59 &&
        second in 0..59
    ) {
        String.format(
            Locale.US,
            "%02d:%02d:%02d",
            hour,
            minute,
            second
        )
    } else {
        "--:--:--"
    }

    return "$datePart $timePart"
}

private fun formatTehranTime(timestamp: Long): String {
    val calendar = Calendar.getInstance(
        android.icu.util.TimeZone.getTimeZone("Asia/Tehran"),
        ULocale("fa_IR@calendar=persian")
    )

    calendar.timeInMillis = timestamp

    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DATE)

    val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("Asia/Tehran")

    val date = String.format(
        Locale.US,
        "%04d/%02d/%02d",
        year,
        month,
        day
    )

    return "$date ${formatter.format(Date(timestamp))}"
}

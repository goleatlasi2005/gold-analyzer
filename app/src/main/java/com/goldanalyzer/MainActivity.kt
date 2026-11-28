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
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Instant
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
var ayiarOrderBook by remember {
    mutableStateOf<AyiarOrderBook?>(null)
}
var mesghalQuote by remember {
    mutableStateOf<MesghalQuote?>(null)
}
var mesghalPrices by remember {
    mutableStateOf<MesghalPrices?>(null)
}
var ayiarCandles by remember {  
    mutableStateOf<CandleSeries?>(null)  
}  
var xauCandles by remember {
    mutableStateOf<CandleSeries?>(null)
}
var ayiarDailyCandles by remember {  
    mutableStateOf<CandleSeries?>(null)  
}  

// نتیجه تحلیل تایم‌فریم انتخاب‌شده؛ همین نتیجه در سیگنال ترکیبی هم مصرف می‌شود.  
var selectedTimeframeAnalysis by remember {  
    mutableStateOf<TimeframeAnalysis?>(null)  
}  

var selectedAnalysisLoading by remember {  
    mutableStateOf(false)  
}  

var selectedAnalysisError by remember {  
    mutableStateOf<String?>(null)  
}  
// وضعیت تحلیل اختصاصی انس جهانی
var xauTimeframeAnalysis by remember {
    mutableStateOf<TimeframeAnalysis?>(null)
}

var xauAnalysisLoading by remember {
    mutableStateOf(false)
}

var xauAnalysisError by remember {
    mutableStateOf<String?>(null)
}
var priceLoading by remember { mutableStateOf(false) }  
var chartLoading by remember { mutableStateOf(false) }  
var refreshing by remember { mutableStateOf(false) }  

var priceError by remember { mutableStateOf<String?>(null) }  
var chartError by remember { mutableStateOf<String?>(null) }
var refreshStatus by remember { mutableStateOf<String?>(null) }
var tgjuPanelRefreshKey by remember { mutableStateOf(0) }

// وضعیت سیگنال ترکیبی کوتاه‌مدت  
var shortTermCombined by remember {  
    mutableStateOf<CombinedPeriodResult?>(null)  
}  

var shortTermCombinedLoading by remember {  
    mutableStateOf(false)  
}  

var shortTermCombinedError by remember {  
    mutableStateOf<String?>(null)  
}  
// وضعیت سیگنال ترکیبی کوتاه‌مدت انس جهانی
var xauShortTermCombined by remember {
    mutableStateOf<CombinedPeriodResult?>(null)
}

var xauShortTermCombinedLoading by remember {
    mutableStateOf(false)
}

var xauShortTermCombinedError by remember {
    mutableStateOf<String?>(null)
}
// وضعیت سیگنال ترکیبی میان‌مدت  
var mediumTermCombined by remember {  
    mutableStateOf<CombinedPeriodResult?>(null)  
}  

var mediumTermCombinedLoading by remember {  
    mutableStateOf(false)  
}  

var mediumTermCombinedError by remember {  
    mutableStateOf<String?>(null)  
}  

val goldRepository = remember { GoldSpotRepository() }
val talasanjRepository = remember { TalasanjRepository() }
// وضعیت سیگنال ترکیبی میان‌مدت انس جهانی
var xauMediumTermCombined by remember {
    mutableStateOf<CombinedPeriodResult?>(null)
}

var xauMediumTermCombinedLoading by remember {
    mutableStateOf(false)
}

var xauMediumTermCombinedError by remember {
    mutableStateOf<String?>(null)
}
val ayiarRepository = remember { AyiarRepository() }
val ayiarMarketRepository = remember {
    AyiarMarketCandleRepository(ayiarRepository)
}
val xauCandleRepository = remember { XauCandleRepository() }
val mesghalRepository = remember { MesghalRepository() }
val refreshMutex = remember { Mutex() }  
val combinedRefreshMutex = remember { Mutex() }  
val scope = rememberCoroutineScope()  

val markets = listOf(  
    "طلای ۱۸ عیار",  
    "صندوق طلای عیار",
    "مظنه طلا",
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
    val xauRequiredCandles = if (selectedTimeframe == "۳۰ دقیقه") 160 else requiredCandles
val longEmaPeriod = if (isHourlyTimeframe) 100 else 200  

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
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {  
        chartError = e.message ?: "خطا در دریافت نمودار"  
    } finally {  
        chartLoading = false  
    }  
}  
suspend fun loadXauChart() {
    if (xauCandles?.candles.isNullOrEmpty()) {
        chartLoading = true
    }

    chartError = null

    try {
        val newCandles = xauCandleRepository.getCandles(
            intervalMinutes = intervalMinutes,
            forceRefresh = true
        )

        if (newCandles.candles.isEmpty()) {
            chartError = "کندلی برای نمایش انس دریافت نشد."
        } else {
            xauCandles = newCandles
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        chartError = e.message ?: "خطا در دریافت نمودار انس"
    } finally {
        chartLoading = false
    }
}
suspend fun refreshShortTermCombined() {  
    combinedRefreshMutex.withLock {  
        shortTermCombinedLoading = true  
        shortTermCombinedError = null  

        try {  
            shortTermCombined =  
                CombinedSignalCoordinator.loadShortTerm(  
                    repository = ayiarMarketRepository,  
                )  
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {  
            shortTermCombinedError =  
                e.message ?: "خطا در محاسبه سیگنال کوتاه‌مدت"  
        } finally {  
            shortTermCombinedLoading = false  
        }  
    }  
}  
suspend fun refreshXauShortTermCombined() {
    combinedRefreshMutex.withLock {
        xauShortTermCombinedLoading = true
        xauShortTermCombinedError = null

        try {
            xauShortTermCombined =
                CombinedSignalCoordinator.loadShortTerm(
                    repository = xauCandleRepository
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            xauShortTermCombinedError =
                e.message ?: "خطا در محاسبه سیگنال کوتاه‌مدت انس"
        } finally {
            xauShortTermCombinedLoading = false
        }
    }
}
suspend fun refreshMediumTermCombined() {  
    combinedRefreshMutex.withLock {  
        mediumTermCombinedLoading = true  
        mediumTermCombinedError = null  

        try {  
            mediumTermCombined =  
                CombinedSignalCoordinator.loadMediumTerm(  
                    repository = ayiarMarketRepository,
                )  
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {  
            mediumTermCombinedError =  
                e.message ?: "خطا در محاسبه سیگنال میان‌مدت"  
        } finally {  
            mediumTermCombinedLoading = false  
        }  
    }  
}  
suspend fun refreshXauMediumTermCombined() {
    combinedRefreshMutex.withLock {
        xauMediumTermCombinedLoading = true
        xauMediumTermCombinedError = null

        try {
            xauMediumTermCombined =
                CombinedSignalCoordinator.loadMediumTerm(
                    repository = xauCandleRepository
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            xauMediumTermCombinedError =
                e.message ?: "خطا در محاسبه سیگنال میان‌مدت انس"
        } finally {
            xauMediumTermCombinedLoading = false
        }
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
                    val talasanj = talasanjRepository.getPrices()
                    val observedAt =
                        Instant.parse(talasanj.updatedAt).toEpochMilli()
                    val ageSeconds =
                        ((System.currentTimeMillis() - observedAt) / 1000L)
                            .coerceAtLeast(0L)

                    spotQuote = GoldSpotQuote(
                        priceUsdPerTroyOunce = talasanj.ounceUsd,
                        source = "Talasanj",
                        observedAtMillis = observedAt,
                        ageSeconds = ageSeconds,
                        isFresh = ageSeconds <= 180L,
                        previousDailyCloseUsd = spotQuote?.previousDailyCloseUsd,
                        changePercent = talasanj.ounceChangePercent
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    try {
                        spotQuote = goldRepository.getXauUsdSpot()
                        priceError =
                            "دریافت از طلاسنج ناموفق بود؛ قیمت از منبع پشتیبان دریافت شد."
                    } catch (fallbackError: CancellationException) {
                        throw fallbackError
                    } catch (fallbackError: Exception) {
                        priceError =
                            fallbackError.message
                                ?: e.message
                                ?: "خطا در دریافت قیمت انس"
                    }
                } finally {
                    priceLoading = false
                }
            }

            "صندوق طلای عیار" -> {
                priceLoading = true
                try {
                    loadAyiarPrice()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    priceError =
                        e.message ?: "خطا در دریافت قیمت عیار"
                } finally {
                    priceLoading = false
                }

                loadAyiarChart()
            }

            "مظنه طلا" -> {
                priceLoading = true
                try {
                    mesghalPrices = mesghalRepository.getLivePrices()
                    val availableQuotes = listOf(
    mesghalPrices?.talasanj,
    mesghalPrices?.tgju,
    mesghalPrices?.nerkhAra
).filterNotNull()

mesghalQuote = availableQuotes.firstOrNull { it.isFresh }
    ?: availableQuotes.firstOrNull()

priceError = when {
    mesghalQuote == null ->
        "قیمت مظنه از هیچ‌یک از منابع دریافت نشد."
    !mesghalQuote!!.isFresh ->
        "هشدار: آخرین قیمت مظنه قدیمی است."
    else -> null
}
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    priceError =
                        e.message ?: "خطا در دریافت قیمت مظنه"
                } finally {
                    priceLoading = false
                }
            }

            else -> {
                tgjuPanelRefreshKey++
                priceError = null
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
                        val talasanj = talasanjRepository.getPrices()
val observedAt =
    Instant.parse(talasanj.updatedAt).toEpochMilli()

val now = System.currentTimeMillis()

if (observedAt <= 0L || observedAt > now + 60_000L) {
    error("زمان ثبت قیمت انس معتبر نیست.")
}

val ageSeconds =
    ((now - observedAt) / 1000L).coerceAtLeast(0L)

val isActuallyFresh =
    observedAt <= now && ageSeconds <= 180L
spotQuote = GoldSpotQuote(
    priceUsdPerTroyOunce = talasanj.ounceUsd,
    source = "Talasanj",
    observedAtMillis = observedAt,
    ageSeconds = ageSeconds,
    isFresh = isActuallyFresh,
    previousDailyCloseUsd = spotQuote?.previousDailyCloseUsd,
changePercent = talasanj.ounceChangePercent
)
                        priceError = null  
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
    try {
        spotQuote = goldRepository.getXauUsdSpot()
        priceError =
            "طلاسنج در دسترس نیست؛ قیمت از منبع پشتیبان دریافت شد."
    } catch (fallbackError: CancellationException) {
        throw fallbackError
    } catch (fallbackError: Exception) {
        priceError =
            fallbackError.message ?: e.message ?: "خطا در دریافت قیمت انس"
    }
                    }
                    finally {
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
    ayiarOrderBook = ayiarRepository.getOrderBook()
    priceError = null
                        } catch (e: CancellationException) {
                            throw e
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
        "مظنه طلا" -> {
            while (true) {
                refreshMutex.withLock {
                    priceLoading = mesghalQuote == null
                    try {
                        mesghalPrices = mesghalRepository.getLivePrices()
val availableQuotes = listOf(
    mesghalPrices?.talasanj,
    mesghalPrices?.tgju,
    mesghalPrices?.nerkhAra
).filterNotNull()

mesghalQuote = availableQuotes.firstOrNull { it.isFresh }
    ?: availableQuotes.firstOrNull()

priceError = when {
    mesghalQuote == null ->
        "قیمت مظنه از هیچ‌یک از منابع دریافت نشد."
    !mesghalQuote!!.isFresh ->
        "هشدار: آخرین قیمت مظنه قدیمی است."
    else -> null
}

                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        priceError = e.message ?: "خطا در دریافت قیمت مظنه"
                    } finally {
                        priceLoading = false
                    }
                }
                delay(60_000L)
            }
        }
        else -> {
    // پنل TGJU دریافت و به‌روزرسانی قیمت را مستقلاً انجام می‌دهد.
    priceError = null
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
    } else if (selectedMarket == "انس جهانی طلا") {
        while (true) {
            refreshMutex.withLock {
                loadXauChart()
            }

            delay(chartRefreshIntervalMillis(intervalMinutes))
        }
    }
}


// تحلیل اختصاصی تایم‌فریم انتخاب‌شده
LaunchedEffect(selectedMarket, selectedTimeframe) {
    if (selectedMarket == "صندوق طلای عیار") {
        while (true) {
            selectedAnalysisLoading = true
            selectedAnalysisError = null

            try {
                selectedTimeframeAnalysis =
                    TimeframeAnalysisStore.getAnalysis(
                        repository = ayiarMarketRepository,
                        intervalMinutes = intervalMinutes.takeIf { it > 0 }
                    )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                selectedAnalysisError =
                    e.message ?: "خطا در تحلیل تایم‌فریم انتخاب‌شده"
                selectedTimeframeAnalysis = null
            } finally {
                selectedAnalysisLoading = false
            }

            delay(
                if (isAyiarMarketOpen()) {
                    chartRefreshIntervalMillis(intervalMinutes)
                } else {
                    60 * 60 * 1000L
                }
            )
        }
    } else if (selectedMarket == "انس جهانی طلا") {
        while (true) {
            xauAnalysisLoading = true
            xauAnalysisError = null

            try {
                xauTimeframeAnalysis =
                    TimeframeAnalysisStore.getAnalysis(
                        repository = xauCandleRepository,
intervalMinutes = intervalMinutes.takeIf { it > 0 },
forceRefresh = true
                    )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                xauAnalysisError =
                    e.message ?: "خطا در تحلیل تایم‌فریم انس"
                xauTimeframeAnalysis = null
            } finally {
                xauAnalysisLoading = false
            }

            // تحلیل بعدی دقیقاً پس از بسته‌شدن کندل بعدی انجام می‌شود؛
            // نه به فاصله کامل تایم‌فریم از زمان پایان درخواست قبلی.
            delay(
    xauNextAnalysisDelayMillis(
        analysis = xauTimeframeAnalysis,
        intervalMinutes = intervalMinutes
    )
)
        }
    }
}

// دریافت اولیه و به‌روزرسانی سیگنال ترکیبی کوتاه‌مدت  
LaunchedEffect(selectedMarket) {  
    if (selectedMarket == "صندوق طلای عیار") {  
        while (true) {  
            refreshShortTermCombined()  

            delay(  
                if (isAyiarMarketOpen()) {  
                    5 * 60 * 1000L  
                } else {  
                    60 * 60 * 1000L  
                }  
            )  
        }  
    }  
    else if (selectedMarket == "انس جهانی طلا") {
        while (true) {
            refreshXauShortTermCombined()
            // همگام با بسته‌شدن کندل‌های ۵ دقیقه‌ای؛ این زمان‌بندی
            // بسته‌شدن کندل‌های ۱۵ و ۳۰ دقیقه‌ای را نیز پوشش می‌دهد.
            delay(delayUntilNextCandleCloseMillis(5))
        }
    }
}
val ayiarPercent = ayiarQuote?.let {  
    calculatePercentChange(it.currentPrice, it.previousClose)  
}  

// فقط کندل‌های کامل وارد تحلیل می‌شوند.  
val completedCandles = remember(ayiarCandles) {  
    val now = System.currentTimeMillis()  
    ayiarCandles?.candles.orEmpty()  
        .filter { it.closeTime <= now }  
        .sortedBy { it.openTime }  
}  

// فقط نتیجه‌ای استفاده شود که دقیقاً متعلق به تایم‌فریم فعلی است.  
val activeTimeframeAnalysis =  
    selectedTimeframeAnalysis?.takeIf {  
        it.intervalMinutes == intervalMinutes.takeIf { value -> value > 0 }  
    }  
// نتیجه تحلیل انس در تایم‌فریم انتخاب‌شده
val activeXauTimeframeAnalysis =
    xauTimeframeAnalysis?.takeIf {
        it.intervalMinutes == intervalMinutes.takeIf { value -> value > 0 }
    }
// سیگنال اختصاصی و ترکیبی هر دو از همین نتیجه تحلیل استفاده می‌کنند.  
val tradingSignal = if (selectedMarket == "صندوق طلای عیار") {  
    activeTimeframeAnalysis?.signal  
} else {  
    null  
}  
val ayiarSignalPricing = tradingSignal?.let { signal ->
    priceAyiarSignal(
        signal = signal,
        orderBook = ayiarOrderBook,
        currentQuotePrice = ayiarQuote?.currentPrice
    )
}
// سیگنال اختصاصی انس جهانی
val xauTradingSignal =
    if (selectedMarket == "انس جهانی طلا") {
        activeXauTimeframeAnalysis?.signal
    } else {
        null
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
// کندل‌های کامل انس جهانی
val completedXauCandles = remember(xauCandles) {
    val now = System.currentTimeMillis()
    xauCandles?.candles.orEmpty()
        .filter { it.closeTime <= now }
        .sortedBy { it.openTime }
}

// محاسبه مستقل حمایت و مقاومت انس
val xauSupportResistanceLevels = remember(
    xauCandles,
    selectedMarket
) {
    if (selectedMarket == "انس جهانی طلا") {
        SupportResistanceEngine.analyze(completedXauCandles)
    } else {
        emptyList()
    }
}
val lastAnalyzedCandle = completedCandles.lastOrNull()  
// اعتبارسنجی قیمت زنده برای فعال بودن سیگنال‌ها
val ayiarQuoteObservedAtMillis = ayiarQuote?.let {
    SignalFreshness.ayiarObservedAtMillis(
        dateValue = it.date,
        timeValue = it.time
    )
}

val ayiarQuoteFresh =
    ayiarQuote != null && isAyiarMarketOpen()

val xauQuoteObservedAtMillis =
    spotQuote?.observedAtMillis

val xauQuoteFresh =
    spotQuote?.isFresh == true
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
if (selectedMarket != "طلای ۱۸ عیار") {
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
                            selectedContainerColor = Color(0xFFFFE8A8),
                            selectedLabelColor = DarkGold
                        )
                    )
                }
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

                    if (selectedMarket != "طلای ۱۸ عیار") {
    Text(
        "تایم‌فریم: $selectedTimeframe",
        color = TextGray
    )
                    }

                    HorizontalDivider(color = Color(0xFFEAEAEA))  

                    when (selectedMarket) {  
                        "انس جهانی طلا" -> {  
                            SpotPriceSourceDrawer(
    quote = spotQuote,
    loading = priceLoading
)
Mt5SourceDrawer()
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

"مظنه طلا" -> {

    val uriHandler = LocalUriHandler.current

    if (priceLoading && mesghalPrices == null) {
        CircularProgressIndicator(color = DarkGold)
        Text("در حال دریافت قیمت از طلاسنج و منابع مقایسه‌ای...")
    }

    val talasanj = mesghalPrices?.talasanj
    val tgju = mesghalPrices?.tgju
    val ara = mesghalPrices?.nerkhAra

    Text("قیمت مظنه طلا")

    ExpandableSection(
        title = "منبع اصلی: طلاسنج",
        initiallyExpanded = true
    ) {
        if (talasanj != null) {
            Text(
                String.format(
                    Locale.US,
                    "%,.0f ریال",
                    talasanj.currentPriceRial
                ),
                color = DarkGold,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            ChangePercentRow(talasanj.changePercent)

Text(
    "زمان قیمت: ${formatTehranTime(talasanj.observedAtMillis)}",
    color = TextGray
)

            Text(
                if (talasanj.isFresh) "داده تازه"
                else "هشدار: داده تازه نیست",
                color = if (talasanj.isFresh) Green else Red
            )
        } else {
            Text("قیمت طلاسنج دریافت نشد.", color = Red)
        }

        Text(
            text = "قیمت‌ها از طلاسنج",
            color = Color(0xFF1565C0),
            modifier = Modifier.clickable {
                uriHandler.openUri("https://talasanj.info")
            }
        )
    }

    ExpandableSection(
        title = "منبع دوم: TGJU",
        initiallyExpanded = false
    ) {
        if (tgju != null) {
            Text(
                String.format(
                    Locale.US,
                    "%,.0f ریال",
                    tgju.currentPriceRial
                ),
                color = DarkGold,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (tgju.isFresh) "داده تازه" else "هشدار: داده تازه نیست",
                color = if (tgju.isFresh) Green else Red
            )
        } else {
            Text("قیمت TGJU دریافت نشد.", color = Red)
        }
    }

    ExpandableSection(
        title = "منبع سوم: نرخ‌آرا",
        initiallyExpanded = false
    ) {
        if (ara != null) {
            Text(
                String.format(
                    Locale.US,
                    "%,.0f ریال",
                    ara.currentPriceRial
                ),
                color = DarkGold,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (ara.isFresh) "داده تازه" else "هشدار: داده تازه نیست",
                color = if (ara.isFresh) Green else Red
            )
        } else {
            Text("قیمت نرخ‌آرا دریافت نشد.", color = Red)
        }
    }

    if (talasanj != null && tgju != null) {
        val difference = kotlin.math.abs(
            talasanj.currentPriceRial - tgju.currentPriceRial
        )

        val differencePercent =
            difference / talasanj.currentPriceRial * 100.0

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "اختلاف طلاسنج و TGJU: ${
                String.format(Locale.US, "%,.0f", difference)
            } ریال",
            fontWeight = FontWeight.Bold,
            color = TextDark
        )

        Text(
            "اختلاف درصدی: ${
                String.format(Locale.US, "%.2f", differencePercent)
            }٪",
            color = TextGray
        )
    }

    if (mesghalPrices == null && !priceLoading) {
        Text("قیمت مظنه از هیچ‌کدام از منابع دریافت نشد.", color = Red)
    }
}


else -> {
    key(tgjuPanelRefreshKey) {
        TgjuGoldPricePanel()
    }
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
            
if (selectedMarket != "طلای ۱۸ عیار") {
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
                val candles = xauCandles?.candles.orEmpty()

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
                            "به‌روزرسانی نمودار ناموفق بود؛ نمودار قبلی نمایش داده می‌شود.",
                            color = TextGray
                        )
                    }
                } else if (chartLoading) {
                    CircularProgressIndicator(color = DarkGold)
                    Text("در حال دریافت نمودار انس...")
                } else {
                    Text(
                        chartError ?: "نمودار انس برای نمایش دریافت نشده است.",
                        color = TextGray
                    )
                }
            }
        }
    }
}
if (selectedMarket != "طلای ۱۸ عیار") {
            ExpandableSection("سیگنال معاملاتی") {  
                when (selectedMarket) {  
                    "صندوق طلای عیار" -> {  
                        when {  
                            selectedAnalysisLoading && activeTimeframeAnalysis == null -> {  
                                CircularProgressIndicator(color = DarkGold)  
                                Text("در حال دریافت داده و محاسبه سیگنال اختصاصی...")  
                            }  

                            activeTimeframeAnalysis == null &&  
                                selectedAnalysisError != null -> {  
                                Text(  
                                    selectedAnalysisError!!,  
                                    color = Red  
                                )  
                            }  

                            activeTimeframeAnalysis == null -> {  
                                Text(  
                                    "هنوز تحلیل این تایم‌فریم آماده نشده است.",  
                                    color = TextGray  
                                )  
                            }  

                            activeTimeframeAnalysis.completedCandles.size < requiredCandles -> {  
                                Text(  
                                    "برای محاسبه سیگنال در این تایم‌فریم حداقل $requiredCandles کندل کامل لازم است.",  
                                    color = TextGray  
                                )  
                                Text(  
                                    "تعداد کندل کامل فعلی: ${activeTimeframeAnalysis.completedCandles.size}",  
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
                                    signal = ayiarSignalPricing?.signal ?: tradingSignal,  
                                    candleCount = activeTimeframeAnalysis.completedCandles.size,  
                                    candleTime = activeTimeframeAnalysis.completedCandles.lastOrNull()?.closeTime,  
oneWayMarket = true,
validateLivePrice = false,
entryPriceNote = ayiarSignalPricing?.sourceNote
                                    )
                            }  
                        }  
                    }  

                                        "انس جهانی طلا" -> {
                        when {
                            xauAnalysisLoading &&
                                activeXauTimeframeAnalysis == null -> {
                                CircularProgressIndicator(color = DarkGold)
                                Text("در حال دریافت داده و محاسبه سیگنال انس...")
                            }

                            activeXauTimeframeAnalysis == null &&
                                xauAnalysisError != null -> {
                                Text(
                                    xauAnalysisError!!,
                                    color = Red
                                )
                            }

                            activeXauTimeframeAnalysis == null -> {
                                Text(
                                    "هنوز تحلیل این تایم‌فریم انس آماده نشده است.",
                                    color = TextGray
                                )
                            }

                            activeXauTimeframeAnalysis.completedCandles.size < xauRequiredCandles -> {
                                Text(
                                    "برای محاسبه سیگنال حداقل $xauRequiredCandles کندل کامل لازم است.",
                                    color = TextGray
                                )
                                Text(
                                    "تعداد کندل کامل فعلی: ${activeXauTimeframeAnalysis.completedCandles.size}",
                                    color = DarkGold,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "در این تایم‌فریم فعلاً سیگنال صادر نمی‌شود.",
                                    color = Red
                                )
                            }

                            xauTradingSignal == null -> {
                                Text(
                                    "داده‌های دریافتی برای محاسبه سیگنال انس کافی یا معتبر نیستند.",
                                    color = Red
                                )
                            }

                            else -> {
                                TradingSignalContent(
                                    signal = xauTradingSignal,
                                    candleCount = activeXauTimeframeAnalysis.completedCandles.size,
                                    candleTime = activeXauTimeframeAnalysis.completedCandles.lastOrNull()?.closeTime,
priceInUsd = true,
livePrice = spotQuote?.priceUsdPerTroyOunce,
quoteObservedAtMillis = xauQuoteObservedAtMillis,
quoteFresh = xauQuoteFresh,
                                    candleFreshnessIntervalMinutes = intervalMinutes
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
}
if (selectedMarket != "طلای ۱۸ عیار") {
            // سیگنال ترکیبی کوتاه‌مدت  
            ExpandableSection("سیگنال ترکیبی کوتاه‌مدت") {
    when (selectedMarket) {
        "صندوق طلای عیار" -> {
            CombinedPeriodContent(
                period = shortTermCombined,
                loading = shortTermCombinedLoading,
                error = shortTermCombinedError,
                primaryTimeframeLabel = "۳۰ دقیقه",
                oneWayMarket = true,
                validateLivePrice = false,
                ayiarOrderBook = ayiarOrderBook,
                ayiarCurrentPrice = ayiarQuote?.currentPrice
            )
        }

        "انس جهانی طلا" -> {
            CombinedPeriodContent(
                period = xauShortTermCombined,
                loading = xauShortTermCombinedLoading,
                error = xauShortTermCombinedError,
                primaryTimeframeLabel = "۳۰ دقیقه",
                priceInUsd = true,
                livePrice = spotQuote?.priceUsdPerTroyOunce,
quoteObservedAtMillis = xauQuoteObservedAtMillis,
quoteFresh = xauQuoteFresh,
                validateCandleFreshness = true
            )
        }

        else -> {
            Text(
                "سیگنال ترکیبی این بازار هنوز فعال نشده است.",
                color = TextGray
            )
        }
    }
            }

            // سیگنال ترکیبی میان‌مدت  
            ExpandableSection(  
                title = "سیگنال ترکیبی میان‌مدت",  
                onExpanded = {  
                    if (selectedMarket == "صندوق طلای عیار" ||
    selectedMarket == "انس جهانی طلا"
) {  
                        scope.launch {  
                            if (selectedMarket == "صندوق طلای عیار") {
    refreshMediumTermCombined()
} else if (selectedMarket == "انس جهانی طلا") {
    refreshXauMediumTermCombined()
                            }
                        }  
                    }  
                }  
            ) {  
                when (selectedMarket) {
    "صندوق طلای عیار" -> {
        CombinedPeriodContent(
            period = mediumTermCombined,
            loading = mediumTermCombinedLoading,
            error = mediumTermCombinedError,
            primaryTimeframeLabel = "روزانه",
            oneWayMarket = true,
            validateLivePrice = false,
            ayiarOrderBook = ayiarOrderBook,
            ayiarCurrentPrice = ayiarQuote?.currentPrice
        )
    }

    "انس جهانی طلا" -> {
        CombinedPeriodContent(
            period = xauMediumTermCombined,
            loading = xauMediumTermCombinedLoading,
            error = xauMediumTermCombinedError,
            primaryTimeframeLabel = "روزانه",
            priceInUsd = true,
            livePrice = spotQuote?.priceUsdPerTroyOunce,
quoteObservedAtMillis = xauQuoteObservedAtMillis,
quoteFresh = xauQuoteFresh,
            validateCandleFreshness = true
        )
    }

    else -> {
        Text(
            "سیگنال ترکیبی این بازار هنوز فعال نشده است.",
            color = TextGray
        )
    }
                }
            }  
}
if (selectedMarket != "طلای ۱۸ عیار") {
            ExpandableSection("تحلیل تکنیکال") {  
                if (selectedMarket == "صندوق طلای عیار") {  
                    Text(  
                        "تایم‌فریم: $selectedTimeframe",  
                        color = DarkGold,  
                        fontWeight = FontWeight.Bold  
                    )  

                    Text(  
                        "تعداد کندل‌های کامل: ${activeTimeframeAnalysis?.completedCandles?.size ?: completedCandles.size}",  
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
                    }                  } else if (selectedMarket == "انس جهانی طلا") {
                    val xauCloses = completedXauCandles.map { it.close }

val xauRsi = remember(completedXauCandles) {
    TechnicalIndicators.rsi(xauCloses).lastOrNull()
}

val xauMacd = remember(completedXauCandles) {
    TechnicalIndicators.macd(xauCloses).lastOrNull()
}

val xauBollinger = remember(completedXauCandles) {
    TechnicalIndicators.bollingerBands(xauCloses).lastOrNull()
}
                    Text(
                        "تایم‌فریم: $selectedTimeframe",
                        color = DarkGold,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "تعداد کندل‌های کامل: ${activeXauTimeframeAnalysis?.completedCandles?.size ?: 0}",
                        color = TextGray
                    )

                    Text(
                        "حداقل کندل موردنیاز:$xauRequiredCandles",
                        color = TextGray
                    )

                    Text(
                        "میانگین بلندمدت: EMA${if (selectedTimeframe == "۳۰ دقیقه") 160 else longEmaPeriod}",
                        color = TextGray
                    )

                    if (xauTradingSignal != null) {
                        Text(
                            "جهت روند: ${xauTradingSignal.trend}",
                            color = TextDark,
                            fontWeight = FontWeight.Bold
                        )

                        xauTradingSignal.adx?.let {
                            Text(
                                "ADX: ${String.format(Locale.US, "%.2f", it)}",
                                color = TextDark
                            )
                        }

                        Text(
                            "ATR: ${String.format(Locale.US, "%.2f USD", xauTradingSignal.atr)}",
                            color = TextDark
                        )
HorizontalDivider(color = Color(0xFFEAEAEA))

Text(
    "اندیکاتورهای تکنیکال",
    color = DarkGold,
    fontWeight = FontWeight.Bold
)

Text(
    "RSI (14): ${
        xauRsi?.let { String.format(Locale.US, "%.2f", it) }
            ?: "نامشخص"
    }",
    color = TextDark
)

if (xauMacd != null) {
    Text(
        "MACD: ${String.format(Locale.US, "%.2f", xauMacd.macd)}",
        color = TextDark
    )
    Text(
        "خط سیگنال: ${String.format(Locale.US, "%.2f", xauMacd.signal)}",
        color = TextDark
    )
    Text(
        "هیستوگرام: ${String.format(Locale.US, "%.2f", xauMacd.histogram)}",
        color = TextDark
    )
} else {
    Text("MACD: داده کافی نیست", color = TextGray)
}

if (xauBollinger != null) {
    Text(
        "باند بالایی بولینگر: ${String.format(Locale.US, "%.2f USD", xauBollinger.upper)}",
        color = TextDark
    )
    Text(
        "باند میانی بولینگر: ${String.format(Locale.US, "%.2f USD", xauBollinger.middle)}",
        color = TextDark
    )
    Text(
        "باند پایینی بولینگر: ${String.format(Locale.US, "%.2f USD", xauBollinger.lower)}",
        color = TextDark
    )
} else {
    Text("بولینگر: داده کافی نیست", color = TextGray)
}

HorizontalDivider(color = Color(0xFFEAEAEA))

                        Text(
                            "دلایل تحلیل:",
                            color = DarkGold,
                            fontWeight = FontWeight.Bold
                        )

                        xauTradingSignal.reasons.forEach { reason ->
                            Text(
                                "• $reason",
                                color = TextGray
                            )
                        }
                    } else {
                        Text(
                            "پس از دریافت حداقل $xauRequiredCandles کندل کامل، تحلیل اندیکاتورها نمایش داده می‌شود.",
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
}
if (selectedMarket != "طلای ۱۸ عیار") {
            // کشوی مستقل حمایت و مقاومت  
            ExpandableSection("حمایت و مقاومت") {  
                if (selectedMarket == "انس جهانی طلا") {
    if (completedXauCandles.size < 60) {
        Text(
            "برای محاسبه حمایت و مقاومت انس حداقل ۶۰ کندل کامل لازم است.",
            color = TextGray
        )
        Text(
            "تعداد کندل کامل فعلی: ${completedXauCandles.size}",
            color = DarkGold
        )
    } else if (xauSupportResistanceLevels.isEmpty()) {
        Text(
            "در داده‌های فعلی انس سطح معتبر حمایت یا مقاومت پیدا نشد.",
            color = TextGray
        )
    } else {
        Text(
            "قیمت مبنا: ${String.format(Locale.US, "%,.2f USD", completedXauCandles.last().close)}",
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

        val xauStaticLevels =
            xauSupportResistanceLevels.filter {
                it.kind == "استاتیک"
            }

        if (xauStaticLevels.isEmpty()) {
            Text(
                "سطح استاتیک نزدیک و معتبری پیدا نشد.",
                color = TextGray
            )
        } else {
            xauStaticLevels.forEach { level ->
                SupportResistanceRow(level, priceInUsd = true)
            }
        }

        HorizontalDivider(color = Color(0xFFEAEAEA))

        Text(
            "سطوح داینامیک",
            color = DarkGold,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        val xauDynamicLevels =
            xauSupportResistanceLevels.filter {
                it.kind == "داینامیک"
            }

        if (xauDynamicLevels.isEmpty()) {
            Text(
                "سطح داینامیک در دسترس نیست.",
                color = TextGray
            )
        } else {
            xauDynamicLevels.forEach { level ->
                SupportResistanceRow(level, priceInUsd = true)
            }
        }

        Text(
            "سطوح استاتیک از سقف‌ها و کف‌های چرخشی قیمت و سطوح داینامیک از EMA20، EMA50 و کیجون ایچیموکو محاسبه می‌شوند.",
            color = TextGray,
            fontSize = 11.sp
        )
    }
} else if (selectedMarket != "صندوق طلای عیار") {
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
}
            Text(  
                "هشدار: سیگنال‌ها خروجی محاسبات تکنیکال هستند و احتمال سود یا زیان را تضمین نمی‌کنند. این نسخه هنوز بک‌تست عملکرد ندارد.",  
                color = TextGray,  
                fontSize = 12.sp  
            )  
}
            Spacer(Modifier.height(12.dp))  
        }   
}
}

@Composable
private fun CombinedPeriodContent(
    period: CombinedPeriodResult?,
    loading: Boolean,
    error: String?,
    primaryTimeframeLabel: String,
    oneWayMarket: Boolean = false,
    priceInUsd: Boolean = false,
    livePrice: Double? = null,
    quoteObservedAtMillis: Long? = null,
    quoteFresh: Boolean = false,
    validateLivePrice: Boolean = true,
    validateCandleFreshness: Boolean = false,
    ayiarOrderBook: AyiarOrderBook? = null,
    ayiarCurrentPrice: Double? = null
) {
    if (period == null) {
        if (loading) {
            CircularProgressIndicator(color = DarkGold)
            Text(
                "در حال دریافت داده‌های تایم‌فریم‌ها و محاسبه سیگنال ترکیبی...",
                color = TextGray
            )
        } else {
            Text(
                error ?: "هنوز داده‌ای برای سیگنال ترکیبی دریافت نشده است.",
                color = Red
            )
        }
        return
    }

    if (loading) {
        Text(
            "در حال به‌روزرسانی سیگنال‌ها...",
            color = DarkGold
        )
    }

    Text(
        "وضعیت تایم‌فریم‌های تشکیل‌دهنده:",
        color = DarkGold,
        fontWeight = FontWeight.Bold
    )

    period.timeframes.forEach { timeframe ->
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "${timeframe.timeframeLabel} | وزن ${timeframe.weightPercent}٪",
                color = TextDark,
                fontWeight = FontWeight.Bold
            )

            Text(
                "تعداد کندل کامل: ${timeframe.candleCount}",
                color = TextGray,
                fontSize = 13.sp
            )

            timeframe.lastCandleTime?.let {
                Text(
                    "آخرین کندل: ${formatTehranTime(it)}",
                    color = TextGray,
                    fontSize = 12.sp
                )
            }

            val timeframeSignal = timeframe.signal

            if (timeframeSignal == null) {
                Text(
                    timeframe.errorMessage
                        ?: "سیگنال معتبر برای این تایم‌فریم محاسبه نشد.",
                    color = Red,
                    fontSize = 12.sp
                )
            } else {
                val liveInactiveReason = if (validateLivePrice) {
                    SignalFreshness.inactiveReason(
                        signal = timeframeSignal,
                        livePrice = livePrice,
                        observedAtMillis = quoteObservedAtMillis,
                        quoteFresh = quoteFresh
                    )
                } else {
                    null
                }
                val candleInactiveReason = if (
                    validateCandleFreshness && timeframeSignal.type != SignalType.HOLD
                ) {
                    xauCandleInactiveReason(
                        candleTime = timeframe.lastCandleTime,
                        intervalMinutes = timeframeIntervalMinutes(timeframe.timeframeLabel)
                    )
                } else {
                    null
                }
                val inactiveReason = candleInactiveReason ?: liveInactiveReason

                if (inactiveReason != null) {
                    Text(
                        "سیگنال مستقل: غیرفعال / منقضی",
                        color = Red,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        inactiveReason,
                        color = Red,
                        fontSize = 12.sp
                    )
                } else {
                    val direction = when (timeframeSignal.type) {
                        SignalType.BUY -> "خرید"
                        SignalType.SELL -> "فروش"
                        SignalType.HOLD -> "صبر"
                    }

                    Text(
                        "سیگنال مستقل: $direction | امتیاز: ${timeframeSignal.score}",
                        color = when (timeframeSignal.type) {
                            SignalType.BUY -> Green
                            SignalType.SELL -> Red
                            SignalType.HOLD -> DarkGold
                        },
                        fontSize = 13.sp
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFFEAEAEA))
    }

    if (period.combinedSignal == null) {
        Text(
            "سیگنال ترکیبی هنوز صادر نشده است.",
            color = Red,
            fontWeight = FontWeight.Bold
        )

        Text(
            "برای صدور سیگنال، هر سه تایم‌فریم باید داده کافی و سیگنال معتبر داشته باشند.",
            color = TextGray,
            fontSize = 13.sp
        )
    } else {
        val primary = period.timeframes.firstOrNull {
            it.timeframeLabel == primaryTimeframeLabel
        }

        Text(
            "نتیجه نهایی $primaryTimeframeLabel مبنا:",
            color = DarkGold,
            fontWeight = FontWeight.Bold
        )

        val ayiarCombinedPricing = if (oneWayMarket) {
            period.combinedSignal?.let { signal ->
                priceAyiarSignal(
                    signal = signal,
                    orderBook = ayiarOrderBook,
                    currentQuotePrice = ayiarCurrentPrice
                )
            }
        } else {
            null
        }

        TradingSignalContent(
            signal = ayiarCombinedPricing?.signal ?: period.combinedSignal,
            candleCount = primary?.candleCount ?: 0,
            candleTime = primary?.lastCandleTime,
            oneWayMarket = oneWayMarket,
            priceInUsd = priceInUsd,
            showRiskLevels = false,
            livePrice = livePrice,
            quoteObservedAtMillis = quoteObservedAtMillis,
            quoteFresh = quoteFresh,
            validateLivePrice = validateLivePrice,
            candleFreshnessIntervalMinutes = if (validateCandleFreshness) {
                timeframeIntervalMinutes(primaryTimeframeLabel)
            } else {
                null
            },
            entryPriceNote = ayiarCombinedPricing?.sourceNote
        )
    }

    if (error != null) {
        Text(
            "پیام به‌روزرسانی: $error",
            color = Red,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SupportResistanceRow(
    level: SupportResistanceLevel,
    priceInUsd: Boolean = false
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
    if (priceInUsd) {
        String.format(Locale.US, "%,.2f USD", level.price)
    } else {
        formatRial(level.price)
    },
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
    oneWayMarket: Boolean = false,
    priceInUsd: Boolean = false,
    showRiskLevels: Boolean = true,
    livePrice: Double? = null,
    quoteObservedAtMillis: Long? = null,
    quoteFresh: Boolean = false,
    validateLivePrice: Boolean = true,
    candleFreshnessIntervalMinutes: Int? = null,
    entryPriceNote: String? = null
) {
    val formatPrice: (Double) -> String = { value ->
        if (priceInUsd) {
            String.format(Locale.US, "%,.2f USD", value)
        } else {
            formatRial(value)
        }
    }

    val liveInactiveReason = if (validateLivePrice) {
        SignalFreshness.inactiveReason(
            signal = signal,
            livePrice = livePrice,
            observedAtMillis = quoteObservedAtMillis,
            quoteFresh = quoteFresh
        )
    } else {
        null
    }
    val candleInactiveReason = if (
        candleFreshnessIntervalMinutes != null && signal.type != SignalType.HOLD
    ) {
        xauCandleInactiveReason(
            candleTime = candleTime,
            intervalMinutes = candleFreshnessIntervalMinutes
        )
    } else {
        null
    }
    val inactiveReason = candleInactiveReason ?: liveInactiveReason

    val signalIsInactive = inactiveReason != null

    val signalColor = if (signalIsInactive) {
        Red
    } else {
        when (signal.type) {
            SignalType.BUY -> Green
            SignalType.SELL -> Red
            SignalType.HOLD -> DarkGold
        }
    }

    val signalText = if (signalIsInactive) {
        "غیرفعال / منقضی"
    } else {
        when (signal.type) {
            SignalType.BUY -> "خرید"
            SignalType.SELL -> if (oneWayMarket) {
                "فروش واحدهای موجود"
            } else {
                "فروش"
            }
            SignalType.HOLD -> "صبر"
        }
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

            inactiveReason?.let {
                Text(
                    it,
                    color = Red,
                    fontSize = 12.sp
                )
            }

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
                    "قیمت مرجع فروش: ${formatPrice(signal.entryPrice)}",
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
                    "قیمت مرجع ورود: ${formatPrice(signal.entryPrice)}",
                    color = TextDark,
                    fontWeight = FontWeight.Bold
                )
            }

            entryPriceNote?.takeIf { signal.type != SignalType.HOLD }?.let { note ->
                Text(
                    note,
                    color = TextGray,
                    fontSize = 11.sp
                )
            }

            if (
                showRiskLevels &&
                signal.type != SignalType.HOLD &&
                !signalIsInactive &&
                !(oneWayMarket && signal.type == SignalType.SELL)
            ) {
                Text(
                    "حد ضرر: ${formatPrice(signal.stopLoss)}",
                    color = Red
                )

                Text(
                    "حد سود اول: ${formatPrice(signal.takeProfit1)}",
                    color = Green
                )

                Text(
                    "حد سود دوم: ${formatPrice(signal.takeProfit2)}",
                    color = Green
                )

                Text(
                    "حد سود سوم: ${formatPrice(signal.takeProfit3)}",
                    color = Green
                )

                Text(
                    "نسبت ریسک به بازده هدف: ۱ به ${
                        String.format(
                            Locale.US,
                            "%.1f",
                            signal.riskRewardRatio
                        )
                    }",
                    color = DarkGold,
                    fontWeight = FontWeight.Bold
                )
            } else if (signalIsInactive) {
                Text(
                    "سطوح حد سود و ضرر این سیگنال نمایش داده نمی‌شوند؛ قیمت ورود قبلی دیگر معتبر نیست.",
                    color = Red,
                    fontSize = 12.sp
                )
            } else if (oneWayMarket && signal.type == SignalType.SELL && showRiskLevels) {
                Text(
                    "این سیگنال برای خروج از واحدهای موجود است؛ حد سود و ضرر معامله فروش استقراضی محاسبه نمی‌شود.",
                    color = TextGray,
                    fontSize = 12.sp
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
onExpanded: (() -> Unit)? = null,
    initiallyExpanded: Boolean = false,
content: @Composable ColumnScope.() -> Unit
) {
var expanded by remember { mutableStateOf(initiallyExpanded) }

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
            TextButton(  
                onClick = {  
                    val willExpand = !expanded  
                    expanded = willExpand  
                    if (willExpand) onExpanded?.invoke()  
                }  
            ) {  
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

private fun timeframeIntervalMinutes(label: String): Int? {
    return when (label) {
        "۵ دقیقه" -> 5
        "۱۵ دقیقه" -> 15
        "۳۰ دقیقه" -> 30
        "۱ ساعت" -> 60
        "۴ ساعت" -> 240
        "روزانه" -> 0
        else -> null
    }
}

/**
 * سیگنال XAU فقط تا زمانی قابل اجراست که کندل تازه‌ای در بازه مورد انتظار دریافت شود.
 * این کنترل فقط در کارت‌های انس فعال می‌شود و منطق عیار را تغییر نمی‌دهد.
 */
private fun xauCandleInactiveReason(
    candleTime: Long?,
    intervalMinutes: Int?,
    nowMillis: Long = System.currentTimeMillis()
): String? {
    val lastCloseTime = candleTime
        ?: return "زمان آخرین کندل مشخص نیست؛ سیگنال تا دریافت داده معتبر غیرفعال است."

    if (lastCloseTime <= 0L || lastCloseTime > nowMillis) {
        return "زمان آخرین کندل معتبر نیست؛ سیگنال فعلاً قابل اجرا نیست."
    }

    val durationMinutes = intervalMinutes
        ?.takeIf { it > 0 }
        ?.toLong()
        ?: 1440L
    val intervalMillis = durationMinutes * 60_000L
    val allowedAgeMillis = intervalMillis + 120_000L
    val candleAgeMillis = nowMillis - lastCloseTime

    if (candleAgeMillis > allowedAgeMillis) {
        return "از بسته‌شدن آخرین کندل مدت بیشتری از بازه معمول گذشته و کندل تازه‌ای دریافت نشده است؛ احتمالاً بازار انس بسته است یا دریافت داده متوقف شده. تا دریافت کندل جدید، این سیگنال قابل اجرا نیست."
    }

    return null
}

private fun chartRefreshIntervalMillis(intervalMinutes: Int): Long {
return if (intervalMinutes <= 0) {
24 * 60 * 60 * 1000L
} else {
intervalMinutes * 60 * 1000L
}
}

// زمان اجرای تحلیل بعدی را با مرز بسته‌شدن کندل‌ها هماهنگ می‌کند.
// مرزهای کندل‌های منبع XAU بر مبنای زمان Unix هستند.
private fun delayUntilNextCandleCloseMillis(intervalMinutes: Int): Long {
    val intervalMillis = if (intervalMinutes <= 0) {
        24L * 60L * 60L * 1000L
    } else {
        intervalMinutes.toLong() * 60_000L
    }

    val now = System.currentTimeMillis()
    val elapsedInCurrentCandle = now % intervalMillis
    val remaining = intervalMillis - elapsedInCurrentCandle

    // چند ثانیه فرصت برای نهایی‌شدن و انتشار کندل در منبع داده.
    return remaining + 5_000L
}
private fun xauNextAnalysisDelayMillis(
    analysis: TimeframeAnalysis?,
    intervalMinutes: Int
): Long {
    val lastCloseTime = analysis
        ?.completedCandles
        ?.lastOrNull()
        ?.closeTime

    if (lastCloseTime == null || lastCloseTime <= 0L) {
        return 2 * 60 * 1000L
    }

    val durationMillis = (
        if (intervalMinutes > 0) intervalMinutes.toLong()
        else 1440L
    ) * 60_000L

    val candleAge = System.currentTimeMillis() - lastCloseTime

    if (candleAge > durationMillis + 120_000L) {
        return 2 * 60 * 1000L
    }

    return delayUntilNextCandleCloseMillis(intervalMinutes)
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
    "درصد تغییر نسبت به روز قبل: " +
        String.format(Locale.US, "%+.2f%%", it)  
} ?: "درصد تغییر نسبت به روز قبل در دسترس نیست"  

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
private data class AyiarSignalPricing(
    val signal: TradingSignal,
    val sourceNote: String?
)

private fun priceAyiarSignal(
    signal: TradingSignal,
    orderBook: AyiarOrderBook?,
    currentQuotePrice: Double?
): AyiarSignalPricing {

    val fallback = AyiarSignalPricing(
        signal = signal,
        sourceNote = if (signal.type == SignalType.HOLD) {
            null
        } else {
            "قیمت مرجع بر اساس قیمت بسته‌شدن آخرین کندل کامل است؛ دفتر سفارش معتبر در دسترس نیست."
        }
    )

    if (signal.type == SignalType.HOLD) {
        return fallback
    }

    val book = orderBook ?: return fallback

    val age = System.currentTimeMillis() - book.fetchedAtMillis

    if (!isAyiarMarketOpen() || age !in 0L..15_000L) {
        return fallback
    }

    val bid = book.bestBid
    val ask = book.bestAsk
    val quote = currentQuotePrice ?: return fallback

    if (
        !bid.isFinite() ||
        !ask.isFinite() ||
        !quote.isFinite() ||
        bid <= 0.0 ||
        ask < bid ||
        quote <= 0.0 ||
        book.bidVolume <= 0L ||
        book.askVolume <= 0L
    ) {
        return fallback
    }

    val midpoint = (bid + ask) / 2.0
    val spreadPercent = ((ask - bid) / midpoint) * 100.0
    val quoteDifferencePercent =
        (kotlin.math.abs(midpoint - quote) / quote) * 100.0

    if (
        spreadPercent > 1.0 ||
        quoteDifferencePercent > 3.0
    ) {
        return fallback
    }

    val atr = signal.atr

    if (!atr.isFinite() || atr <= 0.0) {
        return fallback
    }

    val entry = if (signal.type == SignalType.BUY) ask else bid
    val risk = atr * 1.5

    val stopLoss: Double
    val takeProfit1: Double
    val takeProfit2: Double
    val takeProfit3: Double

    if (signal.type == SignalType.BUY) {
        stopLoss = entry - risk
        takeProfit1 = entry + risk
        takeProfit2 = entry + risk * 2.0
        takeProfit3 = entry + risk * 3.0
    } else {
        stopLoss = entry + risk
        takeProfit1 = entry - risk
        takeProfit2 = entry - risk * 2.0
        takeProfit3 = entry - risk * 3.0
    }

    return AyiarSignalPricing(
        signal = signal.copy(
            entryPrice = entry,
            stopLoss = stopLoss,
            takeProfit1 = takeProfit1,
            takeProfit2 = takeProfit2,
            takeProfit3 = takeProfit3,
            riskRewardRatio = 3.0
        ),
        sourceNote = if (signal.type == SignalType.BUY) {
            "قیمت ورود بر اساس بهترین سفارش فروش (Ask) ثبت‌شده در دفتر سفارش TSETMC محاسبه شده است."
        } else {
            "قیمت فروش بر اساس بهترین سفارش خرید (Bid) ثبت‌شده در دفتر سفارش TSETMC محاسبه شده است."
        }
    )
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
@Composable
private fun SpotPriceSourceDrawer(
    quote: GoldSpotQuote?,
    loading: Boolean
) {
    var expanded by remember { mutableStateOf(true) }
val uriHandler = LocalUriHandler.current
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(1.dp, Color(0xFFE9DFCA))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "منبع اصلی: طلاسنج",
                        color = TextDark,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "قیمت اصلی انس جهانی",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }

                Text(
                    if (expanded) "⌃" else "⌄",
                    color = DarkGold,
                    fontSize = 22.sp
                )
            }

            if (expanded) {
                HorizontalDivider(color = Color(0xFFE9DFCA))

                if (loading && quote == null) {
                    CircularProgressIndicator(color = DarkGold)
                    Text("در حال دریافت قیمت...")
                }

                quote?.let { q ->
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

                    ChangePercentRow(q.changePercent)

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
        }
    }
    if (quote?.source == "Talasanj") {
    Text(
        text = "قیمت‌ها از طلاسنج",
        color = DarkGold,
        modifier = Modifier.clickable {
            uriHandler.openUri("https://talasanj.info")
        }
    )
    }
}

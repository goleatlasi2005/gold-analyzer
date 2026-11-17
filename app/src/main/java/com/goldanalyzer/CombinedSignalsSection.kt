
package com.goldanalyzer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

private val CombinedGold = Color(0xFF987019)
private val CombinedGreen = Color(0xFF26834A)
private val CombinedRed = Color(0xFFB3261E)
private val CombinedGray = Color(0xFF777777)
private val CombinedDark = Color(0xFF292929)
private val CombinedTehranZone = ZoneId.of("Asia/Tehran")

@Composable
fun CombinedSignalsSection(
    repository: AyiarRepository,
    enabled: Boolean,
    oneWayMarket: Boolean,
    selectedTimeframe: String,
    selectedSeries: CandleSeries?
) {
    val candlesByTimeframe = remember {
        mutableStateMapOf<String, List<Candle>>()
    }

    var expanded by remember { mutableStateOf(false) }

    val selectedLabel = when (selectedTimeframe) {
        "۵ دقیقه" -> "۵ دقیقه"
        "۱۵ دقیقه" -> "۱۵ دقیقه"
        "۳۰ دقیقه" -> "۳۰ دقیقه"
        "۱ ساعت" -> "۱ ساعت"
        "۴ ساعت" -> "۴ ساعت"
        "روزانه" -> "روزانه"
        else -> null
    }

    val expectedInterval = when (selectedTimeframe) {
        "۵ دقیقه" -> "5m"
        "۱۵ دقیقه" -> "15m"
        "۳۰ دقیقه" -> "30m"
        "۱ ساعت" -> "60m"
        "۴ ساعت" -> "240m"
        "روزانه" -> "1d"
        else -> null
    }

    val selectedSeriesIsValid =
        selectedSeries != null &&
        selectedSeries.interval == expectedInterval

    LaunchedEffect(
        enabled,
        repository,
        selectedTimeframe,
        selectedSeries
    ) {
        if (!enabled) return@LaunchedEffect

        if (
            selectedSeriesIsValid &&
            selectedLabel != null &&
            selectedSeries != null
        ) {
            candlesByTimeframe[selectedLabel] =
                selectedSeries.candles
        }

        coroutineScope {
            val frames = listOf(
                Triple("۵ دقیقه", 5, 5 * 60_000L),
                Triple("۱۵ دقیقه", 15, 15 * 60_000L),
                Triple("۳۰ دقیقه", 30, 30 * 60_000L),
                Triple("۱ ساعت", 60, 60 * 60_000L),
                Triple("۴ ساعت", 240, 4 * 60 * 60_000L)
            )

            frames.forEach { (label, minutes, refreshMillis) ->
                launch {
                    // تایم‌فریم انتخاب‌شده از همان داده بخش اختصاصی استفاده می‌کند.
                    if (selectedSeriesIsValid && label == selectedLabel) {
                        return@launch
                    }

                    while (true) {
                        try {
                            val series = repository.getCandles(
                                date = LocalDate.now(CombinedTehranZone),
                                intervalMinutes = minutes
                            )

                            if (series.candles.isNotEmpty()) {
                                candlesByTimeframe[label] = series.candles
                            }
                        } catch (_: Exception) {
                            // داده قبلی حفظ می‌شود
                        }

                        if (isCombinedAyiarMarketOpen()) {
                            delay(refreshMillis)
                        } else {
                            while (!isCombinedAyiarMarketOpen()) {
                                delay(60_000L)
                            }
                        }
                    }
                }
            }

            launch {
                if (
                    selectedSeriesIsValid &&
                    selectedLabel == "روزانه"
                ) {
                    return@launch
                }

                while (true) {
                    try {
                        val daily = repository.getDailyCandles(500)
                        if (daily.candles.isNotEmpty()) {
                            candlesByTimeframe["روزانه"] = daily.candles
                        }
                    } catch (_: Exception) {
                        // داده قبلی حفظ می‌شود
                    }

                    delay(24 * 60 * 60 * 1000L)
                }
            }
        }
    }

    val snapshot = candlesByTimeframe.toMap()

    val shortAnalysis = remember(snapshot) {
        MultiTimeframeSignalEngine.analyzeShortTerm(snapshot)
    }

    val mediumAnalysis = remember(snapshot) {
        MultiTimeframeSignalEngine.analyzeMediumTerm(snapshot)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(1.dp, Color(0xFFE9DFCA))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "سیگنال ترکیبی چندتایم‌فریمی",
                    color = CombinedGold,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) "بستن ▲" else "نمایش ▼",
                        color = CombinedGold
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = Color(0xFFEAEAEA))

                Text(
                    "سیگنال کوتاه‌مدت",
                    color = CombinedDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                CombinedSignalResult(
                    analysis = shortAnalysis,
                    oneWayMarket = oneWayMarket,
                    title = "ترکیب ۵، ۱۵ و ۳۰ دقیقه"
                )

                HorizontalDivider(color = Color(0xFFEAEAEA))

                Text(
                    "سیگنال میان‌مدت",
                    color = CombinedDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                CombinedSignalResult(
                    analysis = mediumAnalysis,
                    oneWayMarket = oneWayMarket,
                    title = "ترکیب ۱ ساعت، ۴ ساعت و روزانه"
                )

                Text(
                    "سیگنال فقط پس از دریافت داده کافی از هر سه تایم‌فریم صادر می‌شود.",
                    color = CombinedGray,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CombinedSignalResult(
    analysis: CombinedSignalAnalysis,
    oneWayMarket: Boolean,
    title: String
) {
    val signal = analysis.signal

    if (signal == null) {
        Text(
            "در انتظار داده کافی از تایم‌فریم‌ها...",
            color = CombinedGray
        )

        Text(
            "تایم‌فریم‌های آماده: ${
                analysis.availableTimeframes.joinToString("، ")
                    .ifEmpty { "هیچ‌کدام" }
            }",
            color = CombinedGold,
            fontSize = 12.sp
        )

        if (analysis.missingTimeframes.isNotEmpty()) {
            Text(
                "داده ناکافی: ${analysis.missingTimeframes.joinToString("، ")}",
                color = CombinedRed,
                fontSize = 12.sp
            )
        }

        return
    }

    val signalColor = when (signal.type) {
        SignalType.BUY -> CombinedGreen
        SignalType.SELL -> CombinedRed
        SignalType.HOLD -> CombinedGold
    }

    val signalText = when (signal.type) {
        SignalType.BUY -> "خرید"
        SignalType.SELL ->
            if (oneWayMarket) "فروش واحدهای موجود" else "فروش"
        SignalType.HOLD -> "صبر"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            title,
            color = CombinedGray,
            fontSize = 12.sp
        )

        Text(
            "نتیجه: $signalText",
            color = signalColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "امتیاز ترکیبی: ${signal.score} از ۱۰۰",
            color = CombinedDark,
            fontWeight = FontWeight.Bold
        )

        Text(
            "قدرت محاسباتی سیگنال: ${signal.confidencePercent}٪",
            color = CombinedGray
        )

        Text(
            "روند کلی: ${signal.trend}",
            color = CombinedDark
        )

        if (signal.type == SignalType.BUY) {
            Text(
                "قیمت مرجع ورود: ${combinedFormatRial(signal.entryPrice)}",
                color = CombinedDark
            )
            Text(
                "حد ضرر: ${combinedFormatRial(signal.stopLoss)}",
                color = CombinedRed
            )
            Text(
                "حد سود اول: ${combinedFormatRial(signal.takeProfit1)}",
                color = CombinedGreen
            )
            Text(
                "حد سود دوم: ${combinedFormatRial(signal.takeProfit2)}",
                color = CombinedGreen
            )
            Text(
                "حد سود سوم: ${combinedFormatRial(signal.takeProfit3)}",
                color = CombinedGreen
            )
            Text(
                "نسبت ریسک به بازده هدف: ۱ به ۳",
                color = CombinedGold,
                fontWeight = FontWeight.Bold
            )
        } else if (signal.type == SignalType.SELL && oneWayMarket) {
            Text(
                "قیمت مرجع فروش: ${combinedFormatRial(signal.entryPrice)}",
                color = CombinedDark
            )
            Text(
                "فروش فقط در صورت داشتن واحدهای موجود امکان‌پذیر است؛ این سیگنال به معنی فروش استقراضی نیست.",
                color = CombinedRed,
                fontSize = 12.sp
            )
        } else if (signal.type == SignalType.SELL) {
            Text(
                "قیمت مرجع: ${combinedFormatRial(signal.entryPrice)}",
                color = CombinedDark
            )
        } else {
            Text(
                "فعلاً شرایط ورود به معامله فراهم نیست.",
                color = CombinedGold
            )
        }

        HorizontalDivider(color = Color(0xFFEAEAEA))

        Text(
            "دلایل تحلیل:",
            color = CombinedGold,
            fontWeight = FontWeight.Bold
        )

        signal.reasons.forEach { reason ->
            Text(
                "• $reason",
                color = CombinedGray,
                fontSize = 12.sp
            )
        }
    }
}

private fun combinedFormatRial(value: Double): String {
    return String.format(Locale.US, "%,.0f ریال", value)
}

private fun isCombinedAyiarMarketOpen(): Boolean {
    val now = ZonedDateTime.now(CombinedTehranZone)
    val day = now.dayOfWeek

    val tradingDay =
        day != DayOfWeek.THURSDAY &&
        day != DayOfWeek.FRIDAY

    return tradingDay && now.hour >= 12 && now.hour < 18
}

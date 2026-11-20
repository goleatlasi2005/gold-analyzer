
package com.goldanalyzer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

private val TgjuPanelDarkGold = Color(0xFF987019)
private val TgjuPanelTextGray = Color(0xFF777777)
private val TgjuPanelGreen = Color(0xFF26834A)
private val TgjuPanelRed = Color(0xFFB3261E)

@Composable
fun TgjuGoldPricePanel() {

    var tgjuQuote by remember {
        mutableStateOf<TgjuGoldQuote?>(null)
    }

    var arzQuote by remember {
        mutableStateOf<ArzPriceQuote?>(null)
    }

    var tgjuLoading by remember {
        mutableStateOf(true)
    }

    var arzLoading by remember {
        mutableStateOf(true)
    }

    var tgjuError by remember {
        mutableStateOf<String?>(null)
    }

    var arzError by remember {
        mutableStateOf<String?>(null)
    }

    val tgjuRepository = remember {
        TgjuGoldRepository()
    }

    val arzRepository = remember {
        ArzPriceRepository()
    }

    LaunchedEffect(Unit) {
        while (true) {

            // دریافت مستقل قیمت TGJU
            try {
                tgjuLoading = tgjuQuote == null

                tgjuQuote = tgjuRepository.getLivePrice()
                tgjuError = null

            } catch (e: Exception) {
                tgjuError = e.message ?: "خطا در دریافت TGJU"
            } finally {
                tgjuLoading = false
            }

            // دریافت مستقل قیمت ارزپرایس
            try {
                arzLoading = arzQuote == null

                arzQuote = arzRepository.getLivePrice()
                arzError = null

            } catch (e: Exception) {
                arzError = e.message ?: "خطا در دریافت ارزپرایس"
            } finally {
                arzLoading = false
            }

            delay(60_000L)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {

        Text(
            "مقایسه قیمت لحظه‌ای طلای ۱۸ عیار",
            color = TgjuPanelDarkGold,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider(
            color = Color(0xFFEAEAEA)
        )

        // منبع اول: TGJU
        Text(
            "منبع اول: TGJU",
            color = TgjuPanelDarkGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        if (tgjuLoading && tgjuQuote == null) {
            CircularProgressIndicator(
                color = TgjuPanelDarkGold
            )
            Text(
                "در حال دریافت قیمت TGJU...",
                color = TgjuPanelTextGray
            )
        }

        tgjuQuote?.let { q ->

            Text(
                String.format(
                    Locale.US,
                    "%,.0f تومان",
                    q.currentPriceToman
                ),
                color = TgjuPanelDarkGold,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            val changeColor = when {
                q.changeToman > 0 -> TgjuPanelGreen
                q.changeToman < 0 -> TgjuPanelRed
                else -> TgjuPanelTextGray
            }

            Text(
                "تغییر روزانه: ${
                    String.format(
                        Locale.US,
                        "%+,.0f",
                        q.changeToman
                    )
                } تومان (${
                    String.format(
                        Locale.US,
                        "%+.2f",
                        q.changePercent
                    )
                }٪)",
                color = changeColor,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "بیشترین: ${
                        String.format(
                            Locale.US,
                            "%,.0f",
                            q.highToman
                        )
                    } تومان",
                    color = TgjuPanelGreen,
                    fontSize = 12.sp
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    "کمترین: ${
                        String.format(
                            Locale.US,
                            "%,.0f",
                            q.lowToman
                        )
                    } تومان",
                    color = TgjuPanelRed,
                    fontSize = 12.sp
                )
            }

            Text(
                "زمان ثبت: ${Date(q.observedAtMillis)}",
                color = TgjuPanelTextGray,
                fontSize = 12.sp
            )

            Text(
                if (q.isFresh) {
                    "وضعیت داده: تازه"
                } else {
                    "هشدار: داده TGJU قدیمی است"
                },
                color = if (q.isFresh) {
                    TgjuPanelGreen
                } else {
                    TgjuPanelRed
                },
                fontSize = 12.sp
            )
        }

        if (tgjuError != null) {
            Text(
                "خطای TGJU: $tgjuError",
                color = TgjuPanelRed,
                fontSize = 12.sp
            )
        }

        HorizontalDivider(
            color = Color(0xFFEAEAEA)
        )

        // منبع دوم: ارزپرایس
        Text(
            "منبع دوم: ارزپرایس",
            color = TgjuPanelDarkGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        if (arzLoading && arzQuote == null) {
            CircularProgressIndicator(
                color = TgjuPanelDarkGold
            )
            Text(
                "در حال دریافت قیمت ارزپرایس...",
                color = TgjuPanelTextGray
            )
        }

        arzQuote?.let { q ->

            Text(
                String.format(
                    Locale.US,
                    "%,.0f تومان",
                    q.currentPriceToman
                ),
                color = TgjuPanelDarkGold,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "زمان ثبت: ${Date(q.observedAtMillis)}",
                color = TgjuPanelTextGray,
                fontSize = 12.sp
            )

            Text(
                if (q.isFresh) {
                    "وضعیت داده: تازه"
                } else {
                    "هشدار: داده ارزپرایس قدیمی است"
                },
                color = if (q.isFresh) {
                    TgjuPanelGreen
                } else {
                    TgjuPanelRed
                },
                fontSize = 12.sp
            )
        }

        if (arzError != null) {
            Text(
                "خطای ارزپرایس: $arzError",
                color = TgjuPanelRed,
                fontSize = 12.sp
            )
        }

        // مقایسه دو منبع
        if (tgjuQuote != null && arzQuote != null) {

            HorizontalDivider(
                color = Color(0xFFEAEAEA)
            )

            Text(
                "اختلاف قیمت دو منبع",
                color = TgjuPanelDarkGold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            val difference =
                arzQuote!!.currentPriceToman -
                tgjuQuote!!.currentPriceToman

            val percentDifference =
                if (tgjuQuote!!.currentPriceToman > 0.0) {
                    difference /
                    tgjuQuote!!.currentPriceToman * 100.0
                } else {
                    0.0
                }

            val differenceColor = when {
                difference > 0 -> TgjuPanelGreen
                difference < 0 -> TgjuPanelRed
                else -> TgjuPanelTextGray
            }

            Text(
                String.format(
                    Locale.US,
                    "%,.0f تومان",
                    kotlin.math.abs(difference)
                ),
                color = differenceColor,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                when {
                    difference > 0 ->
                        "قیمت ارزپرایس بالاتر از TGJU است."

                    difference < 0 ->
                        "قیمت ارزپرایس پایین‌تر از TGJU است."

                    else ->
                        "قیمت هر دو منبع برابر است."
                },
                color = differenceColor,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                String.format(
                    Locale.US,
                    "درصد اختلاف: %+.2f٪",
                    percentDifference
                ),
                color = differenceColor,
                fontSize = 13.sp
            )
        }

        HorizontalDivider(
            color = Color(0xFFEAEAEA)
        )

        Text(
            "به‌روزرسانی خودکار هر ۶۰ ثانیه",
            color = TgjuPanelTextGray,
            fontSize = 11.sp
        )

        Text(
            "TGJU منبع اصلی و ارزپرایس منبع مقایسه‌ای است.",
            color = TgjuPanelTextGray,
            fontSize = 11.sp
        )
    }
}

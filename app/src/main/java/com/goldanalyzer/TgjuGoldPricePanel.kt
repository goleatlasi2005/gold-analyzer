
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

    var loading by remember {
        mutableStateOf(true)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    val repository = remember {
        TgjuGoldRepository()
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                loading = tgjuQuote == null
                tgjuQuote = repository.getLivePrice()
                error = null
            } catch (e: Exception) {
                error = e.message ?: "خطا در دریافت قیمت TGJU"
            } finally {
                loading = false
            }

            delay(60_000L)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {

        Text(
            "قیمت لحظه‌ای طلای ۱۸ عیار",
            color = TgjuPanelDarkGold,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider(color = Color(0xFFEAEAEA))

        Text(
            "منبع قیمت: TGJU",
            color = TgjuPanelDarkGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        if (loading && tgjuQuote == null) {
            CircularProgressIndicator(color = TgjuPanelDarkGold)
            Text(
                "در حال دریافت قیمت از TGJU...",
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

        if (error != null) {
            Text(
                "خطای TGJU: $error",
                color = TgjuPanelRed,
                fontSize = 12.sp
            )
        }

        HorizontalDivider(color = Color(0xFFEAEAEA))

        Text(
            "به‌روزرسانی خودکار هر ۶۰ ثانیه",
            color = TgjuPanelTextGray,
            fontSize = 11.sp
        )

        Text(
            "قیمت‌ها فقط از منبع TGJU دریافت می‌شوند.",
            color = TgjuPanelTextGray,
            fontSize = 11.sp
        )
    }
}

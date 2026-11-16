
package com.goldanalyzer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TimeframeSignalTabs(result: ExchangeMultiTimeframeAnalysis) {
    var sectionExpanded by remember(result.exchange, result.symbol) {
        mutableStateOf(false)
    }

    var selectedTimeframe by remember(result.exchange, result.symbol) {
        mutableStateOf("1h")
    }

    var reasonsExpanded by remember(
        result.exchange,
        result.symbol,
        selectedTimeframe
    ) {
        mutableStateOf(false)
    }

    val selectedSignal = result.timeframeSignals
        .firstOrNull { it.timeframe == selectedTimeframe }
        ?.signal

    val signalColor = when (selectedSignal?.type) {
        SignalType.BUY -> Color(0xFF4ADE80)
        SignalType.SELL -> Color(0xFFF87171)
        SignalType.HOLD -> Color(0xFFFBBF24)
        null -> Color.LightGray
    }

    val signalText = when (selectedSignal?.type) {
        SignalType.BUY -> "خرید"
        SignalType.SELL -> "فروش"
        SignalType.HOLD -> "نگهداری"
        null -> "داده کافی نیست"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    sectionExpanded = !sectionExpanded
                }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "سیگنال اختصاصی هر تایم‌فریم",
                color = Color(0xFFB68CFF),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                if (sectionExpanded) "▲" else "▼",
                color = Color(0xFFB68CFF),
                fontSize = 18.sp
            )
        }

        if (sectionExpanded) {
            listOf(
                listOf("5m", "15m", "1h"),
                listOf("4h", "1d", "1w")
            ).forEach { rowFrames ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowFrames.forEach { frame ->
                        FilterChip(
                            selected = selectedTimeframe == frame,
                            onClick = { selectedTimeframe = frame },
                            label = { Text(frame) }
                        )
                    }
                }
            }

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
                        "$selectedTimeframe - سیگنال ${result.exchange}",
                        color = Color(0xFFB68CFF),
                        fontWeight = FontWeight.Bold
                    )

                    if (selectedSignal == null) {
                        Text(
                            "برای این تایم‌فریم داده کافی دریافت نشده است.",
                            color = Color.LightGray
                        )
                    } else {
                        Text(
                            "سیگنال: $signalText",
                            color = signalColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "امتیاز: ${selectedSignal.score} از ۹۰",
                            color = Color.White
                        )

                        Text(
                            "نقطه ورود: ${selectedSignal.entryPrice}",
                            color = Color.White
                        )

                        Text(
                            "حد ضرر: ${selectedSignal.stopLoss}",
                            color = Color(0xFFF87171)
                        )

                        Text(
                            "هدف اول: ${selectedSignal.takeProfit1}",
                            color = Color(0xFF4ADE80)
                        )

                        Text(
                            "هدف دوم: ${selectedSignal.takeProfit2}",
                            color = Color(0xFF4ADE80)
                        )

                        if (selectedSignal.reasons.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        reasonsExpanded = !reasonsExpanded
                                    }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "دلایل سیگنال",
                                    color = Color(0xFFB68CFF),
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    if (reasonsExpanded) "▲" else "▼",
                                    color = Color(0xFFB68CFF)
                                )
                            }

                            if (reasonsExpanded) {
                                selectedSignal.reasons.forEach { reason ->
                                    Text(
                                        "• $reason",
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

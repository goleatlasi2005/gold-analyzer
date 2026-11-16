
package com.goldanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

private val Gold = Color(0xFFD4A52C)
private val DarkGold = Color(0xFF987019)
private val Background = Color(0xFFFFFEFA)
private val TextDark = Color(0xFF292929)
private val TextGray = Color(0xFF777777)

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

    val markets = listOf(
        "طلای ۱۸ عیار",
        "صندوق طلای عیار",
        "انس جهانی طلا"
    )

    val timeframes = listOf(
        "۵ دقیقه", "۱۵ دقیقه", "۳۰ دقیقه",
        "۱ ساعت", "۴ ساعت", "روزانه"
    )

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
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "₿",
                                color = Color.White,
                                fontSize = 29.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selectedMarket == market)
                                Gold
                            else Color(0xFFEAEAEA)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(17.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🪙",
                                fontSize = 25.sp
                            )
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

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0xFFE9DFCA)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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

                        Text(
                            "داده بازار هنوز متصل نشده است.",
                            color = TextGray,
                            fontSize = 14.sp
                        )

                        Text(
                            "پس از اتصال منابع قیمت، تحلیل روند و سیگنال‌ها در این بخش نمایش داده می‌شوند.",
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
                    "EMA  •  RSI  •  MACD  •  Ichimoku  •  Fibonacci  •  ATR  •  ADX",
                    color = DarkGold,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

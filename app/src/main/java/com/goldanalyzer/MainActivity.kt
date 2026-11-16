
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
import java.util.Date
import java.util.Locale

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

    var quote by remember {
        mutableStateOf<GoldSpotQuote?>(null)
    }

    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val repository = remember { GoldSpotRepository() }

    LaunchedEffect(selectedMarket) {
        if (selectedMarket == "انس جهانی طلا") {
            while (true) {
                loading = quote == null
                errorMessage = null

                try {
                    quote = repository.getXauUsdSpot()
                } catch (e: Exception) {
                    errorMessage = e.message ?: "خطا در دریافت قیمت"
                }

                loading = false
                delay(30_000L)
            }
        } else {
            quote = null
            errorMessage = null
            loading = false
        }
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
                    border = BorderStroke(
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

                        HorizontalDivider(color = Color(0xFFEAEAEA))

                        if (selectedMarket == "انس جهانی طلا") {
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

                                    val time = SimpleDateFormat(
                                        "HH:mm:ss",
                                        Locale.getDefault()
                                    ).format(
                                        Date(quote!!.observedAtMillis)
                                    )

                                    Text(
                                        "زمان قیمت: $time",
                                        color = TextGray
                                    )

                                    Text(
                                        if (quote!!.isFresh)
                                            "داده تازه و قابل استفاده"
                                        else
                                            "هشدار: داده تازه نیست؛ سیگنال صادر نمی‌شود",
                                        color = if (quote!!.isFresh)
                                            Color(0xFF26834A)
                                        else Color(0xFFB3261E)
                                    )
                                }

                                errorMessage != null -> {
                                    Text(
                                        "خطا: $errorMessage",
                                        color = Color(0xFFB3261E)
                                    )
                                }
                            }
                        } else {
                            Text(
                                "منبع قیمت این بازار هنوز متصل نشده است.",
                                color = TextGray,
                                fontSize = 14.sp
                            )
                        }

                        Text(
                            "تحلیل تکنیکال پس از اتصال داده‌های کندلی فعال می‌شود.",
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

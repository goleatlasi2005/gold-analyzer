
package com.goldanalyzer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CandlestickChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier
) {
    val visibleCount = 60
    val visibleCandles = candles.takeLast(visibleCount)

    if (visibleCandles.isEmpty()) return

    val closes = candles.map { it.close }

    val ema20 = TechnicalIndicators.ema(closes, 20)
    val ema50 = TechnicalIndicators.ema(closes, 50)
    val bands = TechnicalIndicators.bollingerBands(closes, 20)

    val startIndex = candles.size - visibleCandles.size

    val visibleEma20 = ema20.drop(startIndex)
    val visibleEma50 = ema50.drop(startIndex)
    val visibleBands = bands.drop(startIndex)

    val highest = maxOf(
        visibleCandles.maxOf { it.high },
        visibleBands.mapNotNull { it?.upper }.maxOrNull()
            ?: visibleCandles.maxOf { it.high }
    )

    val lowest = minOf(
        visibleCandles.minOf { it.low },
        visibleBands.mapNotNull { it?.lower }.minOrNull()
            ?: visibleCandles.minOf { it.low }
    )

    val range = (highest - lowest).takeIf { it > 0.0 } ?: 1.0
    val maxVolume = visibleCandles.maxOf { it.volume }
        .takeIf { it > 0.0 } ?: 1.0

    val green = Color(0xFF4ADE80)
    val red = Color(0xFFF87171)
    val yellow = Color(0xFFFFD166)
    val blue = Color(0xFF60A5FA)
    val purple = Color(0xFFB68CFF)
    val bandColor = Color(0xFF60A5FA)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF29213D)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("EMA 20", color = yellow, fontSize = 11.sp)
                Text("EMA 50", color = purple, fontSize = 11.sp)
                Text("Bollinger", color = bandColor, fontSize = 11.sp)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                val chartWidth = size.width
                val chartHeight = size.height * 0.73f
                val volumeTop = size.height * 0.80f
                val volumeHeight = size.height - volumeTop

                val spacing = chartWidth / visibleCandles.size
                val bodyWidth = spacing * 0.62f

                fun priceY(price: Double): Float {
                    return chartHeight -
                        ((price - lowest) / range * chartHeight).toFloat()
                }

                fun drawIndicatorLine(
                    values: List<Double?>,
                    color: Color,
                    stroke: Float
                ) {
                    for (i in 1 until values.size) {
                        val previous = values[i - 1]
                        val current = values[i]

                        if (previous != null && current != null) {
                            drawLine(
                                color = color,
                                start = androidx.compose.ui.geometry.Offset(
                                    spacing * (i - 0.5f),
                                    priceY(previous)
                                ),
                                end = androidx.compose.ui.geometry.Offset(
                                    spacing * (i + 0.5f),
                                    priceY(current)
                                ),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                for (i in 0..4) {
                    val y = chartHeight * i / 4f

                    drawLine(
                        color = Color(0xFF514765),
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                drawLine(
                    color = Color.DarkGray,
                    start = androidx.compose.ui.geometry.Offset(
                        0f, volumeTop - 5.dp.toPx()
                    ),
                    end = androidx.compose.ui.geometry.Offset(
                        chartWidth, volumeTop - 5.dp.toPx()
                    ),
                    strokeWidth = 1.dp.toPx()
                )

                drawIndicatorLine(
                    visibleBands.map { it?.upper },
                    bandColor.copy(alpha = 0.8f),
                    1.2.dp.toPx()
                )

                drawIndicatorLine(
                    visibleBands.map { it?.middle },
                    bandColor.copy(alpha = 0.55f),
                    1.dp.toPx()
                )

                drawIndicatorLine(
                    visibleBands.map { it?.lower },
                    bandColor.copy(alpha = 0.8f),
                    1.2.dp.toPx()
                )

                visibleCandles.forEachIndexed { index, candle ->
                    val centerX = spacing * (index + 0.5f)

                    val highY = priceY(candle.high)
                    val lowY = priceY(candle.low)
                    val openY = priceY(candle.open)
                    val closeY = priceY(candle.close)

                    val candleColor = if (candle.close >= candle.open) {
                        green
                    } else {
                        red
                    }

                    drawLine(
                        color = candleColor,
                        start = androidx.compose.ui.geometry.Offset(
                            centerX, highY
                        ),
                        end = androidx.compose.ui.geometry.Offset(
                            centerX, lowY
                        ),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    val bodyTop = minOf(openY, closeY)
                    val bodyHeight = maxOf(
                        kotlin.math.abs(closeY - openY),
                        2.dp.toPx()
                    )

                    drawRect(
                        color = candleColor,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            centerX - bodyWidth / 2f,
                            bodyTop
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            bodyWidth, bodyHeight
                        )
                    )

                    val barHeight =
                        (candle.volume / maxVolume * volumeHeight).toFloat()

                    drawRect(
                        color = candleColor.copy(alpha = 0.65f),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            centerX - bodyWidth / 2f,
                            size.height - barHeight
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            bodyWidth, barHeight
                        )
                    )
                }

                drawIndicatorLine(
                    visibleEma20,
                    yellow,
                    2.dp.toPx()
                )

                drawIndicatorLine(
                    visibleEma50,
                    purple,
                    2.dp.toPx()
                )
            }

            Text(
                "نمودار حجم معاملات در پایین نمایش داده شده است.",
                color = Color.LightGray,
                fontSize = 11.sp
            )
        }
    }
}


package com.goldanalyzer

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun InvestingSourceDrawer() {
    var expanded by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "منبع مقایسه‌ای: Investing",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(if (expanded) "بستن ▲" else "نمایش قیمت زنده ▼")
            }

            if (expanded) {
                Text(
                    "نمایش مستقیم صفحه رسمی Investing؛ این اطلاعات جدا از قیمت اصلی ربات است.",
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 8.dp
                    ),
                    style = MaterialTheme.typography.bodySmall
                )

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(560.dp),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            webViewClient = WebViewClient()
                            loadUrl(
                                "https://www.investing.com/currencies/xau-usd-chart"
                            )
                            webView = this
                        }
                    },
                    update = { view ->
                        if (view.url.isNullOrBlank()) {
                            view.loadUrl(
                                "https://www.investing.com/currencies/xau-usd-chart"
                            )
                        }
                    }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
}

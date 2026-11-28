package com.hextapp.android.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hextapp.android.R

private const val AADS_MOBILE_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
  html, body { margin: 0; padding: 0; background: transparent; overflow: hidden; display: flex; justify-content: center; align-items: center; }
  iframe { border: 0; width: 100%; max-width: 320px; height: 100px; overflow: hidden; display: block; }
</style>
</head>
<body>
  <iframe data-aa="2463569" src="https://acceptable.a-ads.com/2463569/?size=Adaptive" scrolling="no"></iframe>
</body>
</html>
"""

/**
 * Tarjeta de publicidad integrada y no invasiva para pantallas de catálogo y detalle.
 * Usa un WebView ligero con altura fija (sin popups ni overlays) y abre clics en
 * el navegador externo del sistema.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PromoBannerCard(
    modifier: Modifier = Modifier
) {
    var loadFailed by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.promo_sponsored),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.promo_partner),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (!loadFailed) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                (100 * ctx.resources.displayMetrics.density).toInt()
                            )
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    if (!url.contains("a-ads.com") && (url.startsWith("http://") || url.startsWith("https://"))) {
                                        runCatching {
                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                        return true
                                    }
                                    return false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        loadFailed = true
                                    }
                                }
                            }
                            loadDataWithBaseURL(
                                "https://www.hextapp.com",
                                AADS_MOBILE_HTML,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                )
            } else {
                Text(
                    text = stringResource(R.string.promo_fallback_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

package com.maxlutz.instasaved.detail

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Instagram's own embed page for a Post: plays videos and swipes carousels (ADR-0008). Works for reels too. */
fun embedUrl(shortcode: String) = "https://www.instagram.com/p/$shortcode/embed/"

/** The Post's Embed in a WebView. Taps that leave the Embed (profile, "View on Instagram") open outside the app. */
@SuppressLint("SetJavaScriptEnabled") // The Embed is Instagram's player; it needs JavaScript.
@Composable
fun Embed(shortcode: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame || request.url.path.orEmpty().contains("/embed")) return false
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                        } catch (_: ActivityNotFoundException) {
                            // Nothing can open it: stay on the Embed.
                        }
                        return true
                    }
                }
                loadUrl(embedUrl(shortcode))
            }
        },
        onRelease = { it.destroy() },
    )
}

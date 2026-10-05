package com.maxlutz.instasaved.detail

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Instagram's own embed page for a Post: plays videos and swipes carousels (ADR-0008). Works for reels too. */
fun embedUrl(shortcode: String) = "https://www.instagram.com/p/$shortcode/embed/"

/**
 * Starts the Embed's video, with sound. Best effort (ADR-0008): Instagram builds the page after it has loaded, so
 * this waits up to 10 seconds for the player, and does nothing if it finds no video, or several. A carousel is
 * left alone: it is the only Embed with a list in it.
 */
private const val AUTOPLAY_SCRIPT = """
(function () {
  if (window.instaSavedAutoplay) return;
  window.instaSavedAutoplay = true;
  var tries = 0;
  var timer = setInterval(function () {
    var videos = document.querySelectorAll('video');
    var carousel = document.querySelector('ul');
    if (videos.length === 1 && !carousel) {
      clearInterval(timer);
      if (videos[0].paused) videos[0].play().catch(function () {});
    } else if (carousel || ++tries >= 40) {
      clearInterval(timer);
    }
  }, 250);
})();
"""

/**
 * The Post's Embed in a WebView. A video starts playing on its own, and stops when the app leaves the screen.
 * Taps that leave the Embed (profile, "View on Instagram") open outside the app.
 */
@SuppressLint("SetJavaScriptEnabled") // The Embed is Instagram's player; it needs JavaScript.
@Composable
fun Embed(shortcode: String, modifier: Modifier = Modifier) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    // A WebView keeps playing sound in the background unless it is told to pause.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { webView?.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView?.onResume() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
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

                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(AUTOPLAY_SCRIPT, null)
                    }
                }
                loadUrl(embedUrl(shortcode))
                webView = this
            }
        },
        onRelease = {
            webView = null
            it.destroy()
        },
    )
}

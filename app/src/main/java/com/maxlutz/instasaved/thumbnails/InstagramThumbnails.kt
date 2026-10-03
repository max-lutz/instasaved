package com.maxlutz.instasaved.thumbnails

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One GET request, redirects followed. */
fun interface Http {
    @Throws(IOException::class)
    fun get(url: String): Response

    class Response(val status: Int, val contentType: String, val body: ByteArray)
}

/**
 * Where a Post's Thumbnail comes from (ADR-0008): Instagram's media endpoint, else the `og:image` of the
 * post page. Both are unofficial and work without login.
 */
class InstagramThumbnails(private val http: Http) {
    /** The image bytes, or null if neither source gave an image. Blocking. */
    fun fetch(shortcode: String): ByteArray? {
        // Always /p/: the media endpoint answers 404 under /reel/, whatever the post is.
        val post = "https://www.instagram.com/p/$shortcode/"
        return image("${post}media/?size=m") ?: get(post)?.let(::ogImage)?.let(::image)
    }

    /** A missing or unavailable post answers with a web page (404, or a login page), never an image. */
    private fun image(url: String): ByteArray? =
        get(url)?.takeIf { it.contentType.startsWith("image/") && it.body.isNotEmpty() }?.body

    private fun get(url: String): Http.Response? =
        try {
            http.get(url).takeIf { it.status == HttpURLConnection.HTTP_OK }
        } catch (_: IOException) {
            null
        }

    private fun ogImage(page: Http.Response): String? {
        val html = page.body.toString(Charsets.UTF_8)
        val url = OG_IMAGE.find(html)?.groupValues?.get(1) ?: OG_IMAGE_CONTENT_FIRST.find(html)?.groupValues?.get(1)
        return url?.replace("&amp;", "&")
    }

    private companion object {
        val OG_IMAGE = Regex("""<meta[^>]*property="og:image"[^>]*content="([^"]+)"""")
        val OG_IMAGE_CONTENT_FIRST = Regex("""<meta[^>]*content="([^"]+)"[^>]*property="og:image"""")
    }
}

/**
 * [Http] over the platform's client. It keeps the default User-Agent on purpose: Instagram leaves
 * `og:image` out of the post page for browser User-Agents.
 */
object UrlConnectionHttp : Http {
    private const val TIMEOUT_MS = 15_000

    // A Thumbnail is tens of kilobytes and the post page under one megabyte.
    private const val MAX_BODY_BYTES = 4 * 1024 * 1024

    override fun get(url: String): Http.Response {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) return Http.Response(status, "", ByteArray(0))
            val body = connection.inputStream.use { it.readNBytes(MAX_BODY_BYTES) }
            return Http.Response(status, connection.contentType.orEmpty(), body)
        } finally {
            connection.disconnect()
        }
    }
}

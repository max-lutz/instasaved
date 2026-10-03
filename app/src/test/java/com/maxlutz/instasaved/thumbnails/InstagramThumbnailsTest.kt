package com.maxlutz.instasaved.thumbnails

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class InstagramThumbnailsTest {
    private val media = "https://www.instagram.com/p/ABC123/media/?size=m"
    private val page = "https://www.instagram.com/p/ABC123/"
    private val cdn = "https://scontent.cdninstagram.com/v/1.jpg?stp=dst-jpg&_nc_cat=106"
    private val jpeg = byteArrayOf(1, 2, 3)

    private val requested = mutableListOf<String>()

    /** Answers each URL as listed; any other URL fails like a dropped connection. */
    private fun instagram(vararg answers: Pair<String, Http.Response>) = InstagramThumbnails { url ->
        requested += url
        answers.toMap()[url] ?: throw IOException("unreachable")
    }

    private fun image(bytes: ByteArray = jpeg) = Http.Response(200, "image/jpeg", bytes)

    private fun html(body: String, status: Int = 200) = Http.Response(status, "text/html; charset=utf-8", body.toByteArray())

    private val pageWithOgImage =
        html("""<head><meta property="og:image" content="${cdn.replace("&", "&amp;")}" /></head>""")

    @Test
    fun takesTheImageFromTheMediaEndpoint() {
        val bytes = instagram(media to image()).fetch("ABC123")

        assertArrayEquals(jpeg, bytes)
        assertEquals(listOf(media), requested)
    }

    @Test
    fun fallsBackToThePagesOgImageWhenTheMediaEndpointHasNoImage() {
        val bytes = instagram(media to html("Not found", status = 404), page to pageWithOgImage, cdn to image())
            .fetch("ABC123")

        assertArrayEquals(jpeg, bytes)
        assertEquals(listOf(media, page, cdn), requested)
    }

    @Test
    fun aLoginPageInPlaceOfTheImageIsNotAThumbnail() {
        val bytes = instagram(media to html("Log in"), page to pageWithOgImage, cdn to image()).fetch("ABC123")

        assertArrayEquals(jpeg, bytes)
    }

    @Test
    fun fallsBackWhenTheMediaEndpointIsUnreachable() {
        val bytes = instagram(page to pageWithOgImage, cdn to image()).fetch("ABC123")

        assertArrayEquals(jpeg, bytes)
    }

    @Test
    fun readsOgImageWithItsAttributesInEitherOrder() {
        val contentFirst = html("""<meta content="$cdn" property="og:image">""")

        val bytes = instagram(page to contentFirst, cdn to image()).fetch("ABC123")

        assertArrayEquals(jpeg, bytes)
    }

    @Test
    fun givesNothingWhenThePageHasNoOgImage() {
        val bytes = instagram(page to html("""<meta property="og:title" content="Instagram" />""")).fetch("ABC123")

        assertNull(bytes)
    }

    @Test
    fun givesNothingWhenTheOgImageIsNotAnImage() {
        assertNull(instagram(page to pageWithOgImage, cdn to html("Expired")).fetch("ABC123"))
        assertNull(instagram(page to pageWithOgImage, cdn to image(ByteArray(0))).fetch("ABC123"))
    }

    @Test
    fun givesNothingWhenOffline() {
        assertNull(instagram().fetch("ABC123"))
    }
}

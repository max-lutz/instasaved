package com.maxlutz.instasaved.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostLinkTest {
    private fun shortcodeOf(text: String) = PostLink.find(text)?.shortcode

    @Test
    fun extractsShortcodeFromPostLink() =
        assertEquals("C1a2B3c4D5e", shortcodeOf("https://www.instagram.com/p/C1a2B3c4D5e/"))

    @Test
    fun extractsShortcodeFromReelLink() =
        assertEquals("C1a2B3c4D5e", shortcodeOf("https://www.instagram.com/reel/C1a2B3c4D5e/"))

    @Test
    fun extractsShortcodeFromReelsLink() =
        assertEquals("C1a2B3c4D5e", shortcodeOf("https://www.instagram.com/reels/C1a2B3c4D5e/"))

    @Test
    fun extractsShortcodeFromTvLink() =
        assertEquals("B9x_Y-z0", shortcodeOf("https://www.instagram.com/tv/B9x_Y-z0/"))

    @Test
    fun ignoresQueryString() =
        assertEquals("C1a2B3c4D5e", shortcodeOf("https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=MWQ1ZGUxMzBkMA=="))

    @Test
    fun ignoresQueryStringWithoutTrailingSlash() =
        assertEquals("C1a2B3c4D5e", shortcodeOf("https://www.instagram.com/p/C1a2B3c4D5e?utm_source=ig_web_copy_link"))

    @Test
    fun ignoresFragment() =
        assertEquals("C1a2B3c4D5e", shortcodeOf("https://www.instagram.com/p/C1a2B3c4D5e/#comments"))

    @Test
    fun acceptsLinkWithoutTrailingSlash() =
        assertEquals("C1a2B3c4D5e", shortcodeOf("https://instagram.com/p/C1a2B3c4D5e"))

    @Test
    fun acceptsOtherSubdomainsAndHttp() =
        assertEquals("C1a2B3c4D5e", shortcodeOf("http://m.instagram.com/p/C1a2B3c4D5e/"))

    @Test
    fun acceptsUsernameBeforePostKind() =
        assertEquals("C1a2B3c4D5e", shortcodeOf("https://www.instagram.com/some.user_1/p/C1a2B3c4D5e/"))

    @Test
    fun keepsShortcodeCase() =
        assertEquals("AbCdEf", shortcodeOf("https://www.instagram.com/p/AbCdEf/"))

    @Test
    fun differentLinkShapesOfOnePostGiveTheSameShortcode() {
        val shapes = listOf(
            "https://www.instagram.com/p/C1a2B3c4D5e/",
            "https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=abc",
            "https://instagram.com/reels/C1a2B3c4D5e",
        )
        assertEquals(setOf("C1a2B3c4D5e"), shapes.map(::shortcodeOf).toSet())
    }

    @Test
    fun keepsOriginalUrlIncludingQueryString() {
        val url = "https://www.instagram.com/reel/C1a2B3c4D5e/?igsh=MWQ1ZGUxMzBkMA=="
        assertEquals(PostLink("C1a2B3c4D5e", url), PostLink.find(url))
    }

    @Test
    fun findsLinkInsideSharedMessage() =
        assertEquals(
            PostLink("C1a2B3c4D5e", "https://www.instagram.com/p/C1a2B3c4D5e/?igsh=x"),
            PostLink.find("Check this out https://www.instagram.com/p/C1a2B3c4D5e/?igsh=x\nso good"),
        )

    @Test
    fun rejectsProfileLink() = assertNull(shortcodeOf("https://www.instagram.com/some.user/"))

    @Test
    fun rejectsStoryLink() = assertNull(shortcodeOf("https://www.instagram.com/stories/some.user/3141592653589793238/"))

    @Test
    fun rejectsOtherSites() = assertNull(shortcodeOf("https://www.example.com/p/C1a2B3c4D5e/"))

    @Test
    fun rejectsLookalikeHost() = assertNull(shortcodeOf("https://notinstagram.com/p/C1a2B3c4D5e/"))

    @Test
    fun rejectsTextWithoutLink() = assertNull(shortcodeOf("just some text"))
}

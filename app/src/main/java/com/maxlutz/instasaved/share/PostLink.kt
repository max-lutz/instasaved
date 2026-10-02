package com.maxlutz.instasaved.share

/**
 * A link to an Instagram post or reel: the [shortcode] that identifies the Post (ADR-0007) and the
 * [url] exactly as it was found, kept for opening the post.
 */
data class PostLink(val shortcode: String, val url: String) {
    companion object {
        // Optional username segment (instagram.com/<user>/p/X/), then the post kind and its shortcode.
        // Anything after the shortcode (trailing slash, query string, fragment) is not part of the identity.
        private val pattern = Regex(
            """https?://(?:[a-z0-9-]+\.)?instagram\.com/(?:[A-Za-z0-9._]+/)?(?:p|reels?|tv)/([A-Za-z0-9_-]+)[^\s]*""",
            RegexOption.IGNORE_CASE,
        )

        /** The first Instagram post link in [text] (a bare URL, or a message with a URL in it), or null. */
        fun find(text: String): PostLink? =
            pattern.find(text)?.let { PostLink(shortcode = it.groupValues[1], url = it.value) }
    }
}

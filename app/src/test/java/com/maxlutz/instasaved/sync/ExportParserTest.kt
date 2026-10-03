package com.maxlutz.instasaved.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixtures under `exports/` are real Exports with every value replaced: `snapshot-fr` is a full Export with
 * French labels, `single-post-en` one that held a single post, with English labels and no collections file.
 */
class ExportParserTest {
    private fun fixture(path: String): String =
        checkNotNull(javaClass.getResourceAsStream("/exports/$path")) { "no fixture $path" }
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private val snapshot = parseExport(
        fixture("snapshot-fr/saved_posts.json"),
        fixture("snapshot-fr/saved_collections.json"),
    )

    private fun post(shortcode: String) = snapshot.single { it.shortcode == shortcode }

    /** A one-post Export in the real shape, with English labels. */
    private fun postJson(
        url: String = "https://www.instagram.com/p/AbC/",
        fields: String = """{"label": "Caption", "value": "Hello"},""",
        ownerTitle: String = "Owner",
    ) = """
        {"timestamp": 1790000000, "media": [], "fbid": "1", "label_values": [
          {"label": "URL", "value": "$url", "href": "$url"},
          $fields
          {"title": "$ownerTitle", "dict": [{"title": "", "dict": [
            {"label": "URL", "value": "https://www.instagram.com/p/NotThePost/"},
            {"label": "Name", "value": "Some One"},
            {"label": "Username", "value": "some.one"}]}]}
        ]}
    """

    @Test
    fun parsesEveryPostOfASnapshotNewestFirst() {
        assertEquals(14, snapshot.size)
        assertEquals(snapshot.sortedByDescending { it.savedAt }, snapshot)
    }

    @Test
    fun parsesAPost() =
        assertEquals(
            ExportedPost(
                shortcode = "Fx02PostCZ",
                url = "https://www.instagram.com/p/Fx02PostCZ/",
                caption = "Où partir en octobre ? Voilà nos 5 idées ✈️",
                ownerUsername = "elodie.voyage",
                ownerName = "Élodie Voyage ✈️",
                savedAt = 1_789_949_600_000,
                instagramCollections = listOf("voyages"),
            ),
            post("Fx02PostCZ"),
        )

    @Test
    fun readsShortcodeFromReelLink() =
        assertEquals("https://www.instagram.com/reel/Fx00PostA_/", post("Fx00PostA_").url)

    // sync-spec test 18
    @Test
    fun repairsCaption() =
        assertEquals("C’est la meilleure recette de l’été 🍝 À essayer ! #pâtes #été", post("Fx00PostA_").caption)

    @Test
    fun keepsLineBreaksInCaption() =
        assertEquals("Three stretches I do every morning 🧘\nSave this for later.", post("Fx01Postb-").caption)

    @Test
    fun takesARepeatedCaptionOnce() =
        assertEquals("How to fold a fitted sheet… finally", post("Fx05Postf_").caption)

    @Test
    fun takesTheFirstOfDifferentCaptions() =
        assertEquals("Petit rappel : buvez de l’eau 💧", post("Fx09Postj9").caption)

    @Test
    fun postWithoutCaptionHasNone() = assertNull(post("Fx06PostG-").caption)

    @Test
    fun ownerWithoutNameHasNone() {
        assertEquals("quiet_reader", post("Fx04PostE9").ownerUsername)
        assertNull(post("Fx04PostE9").ownerName)
    }

    @Test
    fun ownerIsNotTheBrandPartner() = assertEquals("account_12", post("Fx12PostAZ").ownerUsername)

    @Test
    fun postInNoInstagramCollectionHasNone() = assertEquals(emptyList<String>(), post("Fx10PostK_").instagramCollections)

    @Test
    fun postInSeveralInstagramCollectionsListsEach() =
        assertEquals(listOf("recettes", "maison"), post("Fx08PostIq").instagramCollections)

    @Test
    fun trimsInstagramCollectionNames() = assertEquals(listOf("Sport"), post("Fx03Postdq").instagramCollections)

    @Test
    fun keepsTheCaseOfInstagramCollectionNames() {
        assertEquals(listOf("romans"), post("Fx04PostE9").instagramCollections)
        assertEquals(listOf("Romans"), post("Fx13Postbq").instagramCollections)
    }

    @Test
    fun instagramCollectionsSharingANameAreOne() {
        assertEquals(listOf("recettes"), post("Fx00PostA_").instagramCollections)
        assertEquals(listOf("recettes"), post("Fx11Postl-").instagramCollections)
    }

    @Test
    fun parsesASinglePostExportWithEnglishLabels() =
        assertEquals(
            listOf(
                ExportedPost(
                    shortcode = "Fx14PostC9",
                    url = "https://www.instagram.com/reel/Fx14PostC9/",
                    caption = "Légende d’exemple n°14 ✨",
                    ownerUsername = "account_15",
                    ownerName = "Compte 15",
                    savedAt = 1_790_070_000_000,
                    instagramCollections = emptyList(),
                ),
            ),
            parseExport(fixture("single-post-en/saved_posts.json"), savedCollectionsJson = null),
        )

    @Test
    fun emptyExportHasNoPosts() = assertEquals(emptyList<ExportedPost>(), parseExport("[]", "[]"))

    @Test
    fun keepsOnePostPerShortcode() {
        val reel = postJson(url = "https://www.instagram.com/reel/AbC/")
        assertEquals(
            listOf("https://www.instagram.com/reel/AbC/"),
            parseExport("[$reel, ${postJson()}]", null).map { it.url },
        )
    }

    @Test
    fun skipsASavedLinkThatIsNotAPost() =
        assertEquals(
            listOf("AbC"),
            parseExport("[${postJson(url = "https://www.instagram.com/some.user/")}, ${postJson()}]", null)
                .map { it.shortcode },
        )

    @Test
    fun blankCaptionIsNoCaption() =
        assertNull(parseExport(postJson(fields = """{"label": "Caption", "value": " "},"""), null).single().caption)

    @Test
    fun ignoresAnInstagramCollectionPostThatIsNotSaved() {
        val collections = """
            [{"timestamp": 1, "media": [], "fbid": "2", "label_values": [
              {"label": "Name", "value": "Travel"},
              {"title": "Media", "dict": [
                {"title": "", "dict": [{"label": "URL", "value": "https://www.instagram.com/p/Other/"}]},
                {"title": "", "dict": [{"label": "URL", "value": "https://www.instagram.com/reel/AbC/?igsh=x"}]}]}
            ]}]
        """
        assertEquals(
            listOf(listOf("Travel")),
            parseExport(postJson(), collections).map { it.instagramCollections },
        )
    }

    @Test
    fun rejectsLabelsInAnUnknownLanguage() {
        assertThrows(ExportFormatException::class.java) { parseExport(postJson(ownerTitle = "Eigentümer"), null) }
    }

    @Test
    fun rejectsAnInstagramCollectionWithoutAKnownNameLabel() {
        val collections = """[{"timestamp": 1, "label_values": [{"label": "Nome", "value": "Travel"}]}]"""
        assertThrows(ExportFormatException::class.java) { parseExport(postJson(), collections) }
    }

    @Test
    fun rejectsTheOlderExportShape() {
        val older = """{"saved_saved_media": [{"title": "some.one", "string_map_data": {}}]}"""
        assertThrows(ExportFormatException::class.java) { parseExport(older, null) }
    }

    @Test
    fun rejectsInvalidJson() {
        val error = assertThrows(ExportFormatException::class.java) { parseExport("<html>", null) }
        assertTrue(error.message!!.contains("saved_posts.json"))
    }

    @Test
    fun rejectsAPostWithoutTimestamp() {
        val post = postJson().replace(""""timestamp": 1790000000,""", "")
        assertThrows(ExportFormatException::class.java) { parseExport(post, null) }
    }

    @Test
    fun repairsLatin1EncodedUtf8() = assertEquals("C’est", repairText("Câ\u0080\u0099est"))

    @Test
    fun repairLeavesPlainTextAlone() = assertEquals("Plain text", repairText("Plain text"))

    @Test
    fun repairLeavesProperTextAlone() {
        assertEquals("C’est l’été", repairText("C’est l’été"))
        assertEquals("été", repairText("été"))
    }
}

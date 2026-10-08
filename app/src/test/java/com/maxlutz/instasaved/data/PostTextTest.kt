package com.maxlutz.instasaved.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PostTextTest {
    private val post = Post(shortcode = "ABC123", url = "https://www.instagram.com/p/ABC123/", addedAt = 1L)

    // Title derivation

    @Test
    fun titleIsTheFirstSentenceWithoutItsFullStop() {
        assertEquals("Best pasta in town", titleFrom("Best pasta in town. Recipe below!"))
    }

    @Test
    fun titleKeepsAnExclamationOrQuestionMark() {
        assertEquals("Wow!", titleFrom("Wow! Look at this."))
        assertEquals("Ever tried this?", titleFrom("Ever tried this? You should."))
    }

    @Test
    fun titleKeepsAnEllipsis() {
        assertEquals("Wait for it...", titleFrom("Wait for it... boom"))
        assertEquals("Wait for it…", titleFrom("Wait for it… boom"))
    }

    @Test
    fun titleStopsAtTheFirstLineBreak() {
        assertEquals("Lemon tart", titleFrom("Lemon tart\n\nIngredients: lemons, sugar."))
    }

    @Test
    fun aFullStopInsideAWordDoesNotEndTheSentence() {
        assertEquals("Version 2.0 is out", titleFrom("Version 2.0 is out. Go get it"))
        assertEquals("Shop at example.com", titleFrom("Shop at example.com"))
    }

    @Test
    fun titleSkipsLeadingBlankLinesAndTrims() {
        assertEquals("Hello", titleFrom("\n  \n  Hello  \nworld"))
    }

    @Test
    fun noDescriptionMeansNoTitle() {
        assertEquals("", titleFrom(""))
        assertEquals("", titleFrom("  \n "))
    }

    @Test
    fun aLongFirstSentenceIsCutAtAWordWithAnEllipsis() {
        val title = titleFrom("word ".repeat(40))

        assertTrue(title.length <= TITLE_MAX_LENGTH)
        assertTrue(title.endsWith("word…"))
    }

    // Editing

    @Test
    fun titleFollowsDescriptionUntilHandEdited() {
        val edited = post.editDescription("Best pasta in town. Recipe below!")

        assertEquals("Best pasta in town", edited.title)
        assertFalse(edited.titleHandEdited)
    }

    @Test
    fun editingDescriptionMarksItHandEdited() {
        assertTrue(post.editDescription("Hello").descriptionHandEdited)
    }

    @Test
    fun aHandEditedTitleNoLongerFollowsDescription() {
        val edited = post.editDescription("First. Second.").editTitle("My pick").editDescription("Changed. Again.")

        assertEquals("My pick", edited.title)
        assertTrue(edited.titleHandEdited)
    }

    @Test
    fun aHandEditedTitleKeepsExactlyWhatWasTyped() {
        assertEquals("  spaced  ", post.editTitle("  spaced  ").title)
        assertEquals("", post.editDescription("Hello").editTitle("").title)
    }

    @Test
    fun unchangedTextIsNotAHandEdit() {
        val described = post.copy(description = "Hello", title = "Hello")

        assertSame(described, described.editDescription("Hello"))
        assertSame(described, described.editTitle("Hello"))
    }

    @Test
    fun postNoteDoesNotTouchTitleOrDescription() {
        val edited = post.copy(description = "Hello", title = "Hello").editPostNote("for Sunday")

        assertEquals("for Sunday", edited.postNote)
        assertEquals("Hello", edited.title)
        assertFalse(edited.titleHandEdited)
        assertFalse(edited.descriptionHandEdited)
    }

    // "Also on Instagram in"

    private fun inInstagramCollections(vararg names: String) = post.copy(instagramCollections = names.toList())

    // sync-spec test 19.
    @Test
    fun alsoOnInstagramInLeavesOutTheCollectionThePostIsIn() {
        assertEquals(listOf("Recipes"), inInstagramCollections("Recipes", "Travel").alsoOnInstagramIn("Travel"))
        assertEquals(listOf("Recipes"), inInstagramCollections("Recipes", " travel").alsoOnInstagramIn("Travel"))
        assertEquals(emptyList<String>(), inInstagramCollections("Travel").alsoOnInstagramIn("Travel"))
    }

    @Test
    fun alsoOnInstagramInNamesThemAllForAPostElsewhere() {
        val post = inInstagramCollections("Recipes", "Travel")

        assertEquals(listOf("Recipes", "Travel"), post.alsoOnInstagramIn(null))
        assertEquals(listOf("Recipes", "Travel"), post.alsoOnInstagramIn("Dinner"))
        assertEquals(emptyList<String>(), this.post.alsoOnInstagramIn(null))
    }
}

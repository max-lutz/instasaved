package com.maxlutz.instasaved.sync

import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.share.PostLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The test-case table of `docs/sync-spec.md`, by its case numbers. */
class SyncRulesTest {
    private fun exported(
        shortcode: String,
        vararg instagramCollections: String,
        caption: String? = null,
        url: String = "https://www.instagram.com/p/$shortcode/",
    ) = ExportedPost(
        shortcode = shortcode,
        url = url,
        caption = caption,
        ownerUsername = "some.one",
        ownerName = "Some One",
        savedAt = 1_790_000_000_000,
        instagramCollections = instagramCollections.toList(),
    )

    /** A Post an earlier Export already brought in. */
    private fun synced(shortcode: String, collection: String? = null, description: String = "") = SyncPost(
        shortcode = shortcode,
        collection = collection,
        title = description,
        description = description,
        ownerUsername = "some.one",
        ownerName = "Some One",
        seenInExport = true,
    )

    private fun collection(name: String, color: Int = PALETTE[0]) = SyncCollection(name, color)

    /** The app once [changes] are applied. */
    private fun AppState.after(changes: SyncChanges): AppState {
        val updated = changes.updatedPosts.associateBy { it.shortcode }
        return copy(
            posts = posts.map { updated[it.shortcode] ?: it } + changes.addedPosts.map { it.post },
            collections = collections + changes.createdCollections,
        )
    }

    private fun SyncChanges.added(shortcode: String) = addedPosts.single { it.post.shortcode == shortcode }.post

    private fun SyncChanges.updated(shortcode: String) = updatedPosts.single { it.shortcode == shortcode }

    // Case 1
    @Test
    fun newPostsLandInTheirInstagramCollectionOrInToSort() {
        val (changes, summary) = planSync(AppState(), listOf(exported("A", "Recipes"), exported("B")))

        assertEquals(listOf(collection("Recipes", PALETTE[0])), changes.createdCollections)
        assertEquals("Recipes", changes.added("A").collection)
        assertEquals(null, changes.added("B").collection)
        assertEquals(2, summary.new)
    }

    @Test
    fun newPostTakesItsTextOwnerAndSavedDateFromTheExport() {
        val export = listOf(exported("A", "Recipes", caption = "Best pasta. Try it!"))

        val added = planSync(AppState(), export).changes.addedPosts.single()

        assertEquals(
            AddedPost(
                post = SyncPost(
                    shortcode = "A",
                    collection = "Recipes",
                    title = "Best pasta",
                    description = "Best pasta. Try it!",
                    ownerUsername = "some.one",
                    ownerName = "Some One",
                    seenInExport = true,
                    instagramCollections = listOf("Recipes"),
                ),
                url = "https://www.instagram.com/p/A/",
                savedAt = 1_790_000_000_000,
            ),
            added,
        )
    }

    // Case 2
    @Test
    fun newPostGoesIntoTheExistingCollectionOfThatNameIgnoringCase() {
        val app = AppState(collections = listOf(collection("recipes")))

        val changes = planSync(app, listOf(exported("A", "Recipes"))).changes

        assertEquals("recipes", changes.added("A").collection)
        assertEquals(emptyList<SyncCollection>(), changes.createdCollections)
    }

    // Case 3
    @Test
    fun postTheUserMovedStaysWhereItIs() {
        val app = AppState(posts = listOf(synced("A", collection = "Travel")), collections = listOf(collection("Travel")))

        val changes = planSync(app, listOf(exported("A", "Recipes"))).changes

        assertEquals("Travel", changes.updated("A").collection)
        assertEquals(emptyList<SyncCollection>(), changes.createdCollections)
    }

    // Case 4
    @Test
    fun deletedCollectionIsNotRecreatedForPostsAlreadyInTheApp() {
        val app = AppState(
            posts = listOf(synced("A", collection = "Travel"), synced("B")),
            collections = listOf(collection("Travel")),
        )

        val changes = planSync(app, listOf(exported("A", "Recipes"), exported("B", "Recipes"))).changes

        assertEquals(emptyList<SyncCollection>(), changes.createdCollections)
        assertEquals("Travel", changes.updated("A").collection)
        assertEquals(null, changes.updated("B").collection)
    }

    // Case 5
    @Test
    fun deletedCollectionIsRecreatedForANewPostOnly() {
        val app = AppState(posts = listOf(synced("A")))

        val changes = planSync(app, listOf(exported("A", "Recipes"), exported("C", "Recipes"))).changes

        assertEquals(listOf("Recipes"), changes.createdCollections.map { it.name })
        assertEquals("Recipes", changes.added("C").collection)
        assertEquals(null, changes.updated("A").collection)
    }

    // Case 6
    @Test
    fun renamedCollectionIsNotFollowed() {
        val app = AppState(
            posts = listOf(synced("A", collection = "🍝 Recipes")),
            collections = listOf(collection("🍝 Recipes")),
        )

        val changes = planSync(app, listOf(exported("A", "Recipes"), exported("C", "Recipes"))).changes

        assertEquals(listOf(collection("Recipes", PALETTE[1])), changes.createdCollections)
        assertEquals("Recipes", changes.added("C").collection)
        assertEquals("🍝 Recipes", changes.updated("A").collection)
    }

    // Cases 7 and 21: the rules are given Recently deleted Posts and traces as one set
    @Test
    fun deletedPostIsIgnored() {
        val app = AppState(deletedShortcodes = setOf("A"))

        val (changes, summary) = planSync(app, listOf(exported("A", "Recipes")))

        assertTrue(changes.isEmpty)
        assertTrue(summary.isEmpty)
    }

    // Case 8
    @Test
    fun postMissingFromTheExportIsLeftAlone() {
        val app = AppState(posts = listOf(synced("A", collection = "Travel")), collections = listOf(collection("Travel")))

        val (changes, summary) = planSync(app, listOf(exported("B")))

        assertEquals(emptyList<SyncPost>(), changes.updatedPosts)
        assertEquals(SyncSummary(new = 1), summary)
    }

    // Case 9
    @Test
    fun exportWithoutPostsChangesNothing() {
        val app = AppState(posts = listOf(synced("A")))

        val (changes, summary) = planSync(app, emptyList())

        assertTrue(changes.isEmpty)
        assertTrue(summary.isEmpty)
    }

    // Case 11a
    @Test
    fun shareInPostTheUserSortedIsMatchedAndLeftInItsCollection() {
        val app = AppState(
            posts = listOf(SyncPost(shortcode = "A", collection = "Travel")),
            collections = listOf(collection("Travel")),
        )

        val (changes, summary) = planSync(app, listOf(exported("A", "Recipes")))

        assertEquals(emptyList<AddedPost>(), changes.addedPosts)
        assertEquals(emptyList<SyncCollection>(), changes.createdCollections)
        assertEquals("Travel", changes.updated("A").collection)
        assertTrue(changes.updated("A").seenInExport)
        assertEquals(0, summary.new)
    }

    // Case 11b
    @Test
    fun shareInPostStillInToSortIsPlacedByItsFirstExport() {
        val app = AppState(posts = listOf(SyncPost(shortcode = "A")))

        val (changes, summary) = planSync(app, listOf(exported("A", "Recipes")))

        assertEquals(listOf("Recipes"), changes.createdCollections.map { it.name })
        assertEquals("Recipes", changes.updated("A").collection)
        assertEquals(emptyList<AddedPost>(), changes.addedPosts)
        assertEquals(0, summary.new)
    }

    @Test
    fun shareInPostIsFilledInByItsFirstExport() {
        val app = AppState(posts = listOf(SyncPost(shortcode = "A")))

        val updated = planSync(app, listOf(exported("A", caption = "Best pasta."))).changes.updated("A")

        assertEquals("Best pasta.", updated.description)
        assertEquals("Best pasta", updated.title)
        assertEquals("some.one", updated.ownerUsername)
        assertEquals("Some One", updated.ownerName)
    }

    // Case 11c
    @Test
    fun postAlreadySeenInAnExportIsNotPlacedAgain() {
        val app = AppState(posts = listOf(synced("A")))

        val changes = planSync(app, listOf(exported("A", "Recipes"))).changes

        assertEquals(null, changes.updated("A").collection)
        assertEquals(listOf("Recipes"), changes.updated("A").instagramCollections)
        assertEquals(emptyList<SyncCollection>(), changes.createdCollections)
    }

    // Case 12
    @Test
    fun changedCaptionUpdatesDescriptionAndTitle() {
        val app = AppState(posts = listOf(synced("A", description = "Old caption")))

        val (changes, summary) = planSync(app, listOf(exported("A", caption = "New caption. More.")))

        assertEquals("New caption. More.", changes.updated("A").description)
        assertEquals("New caption", changes.updated("A").title)
        assertEquals(SyncSummary(captionsUpdated = 1), summary)
    }

    // Case 12
    @Test
    fun changedCaptionLeavesAHandEditedTitle() {
        val post = synced("A", description = "Old caption").copy(title = "My title", titleHandEdited = true)

        val updated = planSync(AppState(listOf(post)), listOf(exported("A", caption = "New caption"))).changes.updated("A")

        assertEquals("New caption", updated.description)
        assertEquals("My title", updated.title)
    }

    // Case 13
    @Test
    fun changedCaptionLeavesAHandEditedDescription() {
        val post = synced("A", description = "My words").copy(descriptionHandEdited = true)

        val (changes, summary) = planSync(AppState(listOf(post)), listOf(exported("A", caption = "New caption")))

        assertTrue(changes.isEmpty)
        assertTrue(summary.isEmpty)
    }

    // Case 14
    @Test
    fun postIsMatchedWhateverTheShapeOfItsLink() {
        val saved = checkNotNull(PostLink.find("https://www.instagram.com/reel/X/?igsh=abc"))
        val inExport = checkNotNull(PostLink.find("https://www.instagram.com/p/X/"))
        val app = AppState(posts = listOf(SyncPost(shortcode = saved.shortcode)))

        val changes = planSync(app, listOf(exported(inExport.shortcode, url = inExport.url))).changes

        assertEquals(emptyList<AddedPost>(), changes.addedPosts)
        assertTrue(changes.updated("X").seenInExport)
    }

    // Case 15
    @Test
    fun sameExportAppliedTwiceChangesNothingTheSecondTime() {
        val app = AppState(
            posts = listOf(
                synced("A", description = "Old caption"),
                SyncPost(shortcode = "B"),
                synced("C"),
            ),
            collections = listOf(collection("Travel")),
            deletedShortcodes = setOf("D"),
        )
        val export = listOf(
            exported("A", "Recipes", caption = "New caption"),
            exported("B", "Recipes", "Travel"),
            exported("C"),
            exported("D", "Recipes"),
            exported("E", "Recipes", "Desserts", caption = "Cake"),
            exported("F"),
        )
        val first = planSync(app, export)

        val (changes, summary) = planSync(app.after(first.changes), export)

        assertFalse(first.changes.isEmpty)
        assertTrue(changes.isEmpty)
        assertTrue(summary.isEmpty)
    }

    // Case 18
    @Test
    fun descriptionIsTheRepairedCaption() {
        val json = """
            {"timestamp": 1790000000, "label_values": [
              {"label": "URL", "value": "https://www.instagram.com/p/A/"},
              {"label": "Caption", "value": "Câ\u0080\u0099est"},
              {"title": "Owner", "dict": []}
            ]}
        """

        val changes = planSync(AppState(), parseExport(json, null)).changes

        assertEquals("C’est", changes.added("A").description)
    }

    // Case 19
    @Test
    fun newPostInSeveralInstagramCollectionsPrefersAnExistingCollection() {
        val app = AppState(collections = listOf(collection("Travel")))

        val changes = planSync(app, listOf(exported("C", "Recipes", "Travel"))).changes

        assertEquals("Travel", changes.added("C").collection)
        assertEquals(emptyList<SyncCollection>(), changes.createdCollections)
        assertEquals(listOf("Recipes", "Travel"), changes.added("C").instagramCollections)
    }

    // Case 20
    @Test
    fun newPostInSeveralInstagramCollectionsCreatesTheFirstAlphabetically() {
        val changes = planSync(AppState(), listOf(exported("C", "Recipes", "Desserts"))).changes

        assertEquals(listOf("Desserts"), changes.createdCollections.map { it.name })
        assertEquals("Desserts", changes.added("C").collection)
    }

    @Test
    fun severalMatchingCollectionsGiveTheFirstAlphabetically() {
        val app = AppState(collections = listOf(collection("Travel"), collection("desserts")))

        val changes = planSync(app, listOf(exported("C", "Travel", "Recipes", "Desserts"))).changes

        assertEquals("desserts", changes.added("C").collection)
    }

    @Test
    fun placementDoesNotDependOnTheOrderOfTheExport() {
        val posts = listOf(exported("A", "Recipes"), exported("C", "Recipes", "Desserts"))

        val inOrder = planSync(AppState(), posts).changes
        val reversed = planSync(AppState(), posts.reversed()).changes

        assertEquals("Desserts", inOrder.added("C").collection)
        assertEquals("Desserts", reversed.added("C").collection)
        assertEquals(setOf("Recipes", "Desserts"), inOrder.createdCollections.map { it.name }.toSet())
    }

    @Test
    fun postsOfOneInstagramCollectionShareTheCollectionCreatedForIt() {
        val changes = planSync(AppState(), listOf(exported("A", "Books"), exported("B", "books"))).changes

        assertEquals(listOf("Books"), changes.createdCollections.map { it.name })
        assertEquals("Books", changes.added("B").collection)
    }

    @Test
    fun createdCollectionsWalkThePalette() {
        val app = AppState(collections = listOf(collection("Travel", PALETTE[0])))

        val changes = planSync(app, listOf(exported("A", "Recipes"), exported("B", "Desserts"))).changes

        assertEquals(listOf(PALETTE[1], PALETTE[2]), changes.createdCollections.map { it.color })
    }

    @Test
    fun postTheExportDoesNotChangeIsNotAnUpdate() {
        val app = AppState(posts = listOf(synced("A", description = "Caption")))

        val (changes, summary) = planSync(app, listOf(exported("A", caption = "Caption")))

        assertTrue(changes.isEmpty)
        assertTrue(summary.isEmpty)
    }
}

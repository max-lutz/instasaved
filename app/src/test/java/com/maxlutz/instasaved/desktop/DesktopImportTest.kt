package com.maxlutz.instasaved.desktop

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maxlutz.instasaved.data.AppDatabase
import com.maxlutz.instasaved.data.Backup
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.updateText
import com.maxlutz.instasaved.desktop.DesktopImport.Summary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DesktopImportTest {
    private lateinit var db: AppDatabase
    private lateinit var desktopImport: DesktopImport
    private val posts get() = db.postDao()
    private val collections get() = db.collectionDao()
    private val tags get() = db.tagDao()
    private val deleted get() = db.recentlyDeletedDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        desktopImport = DesktopImport(db.desktopImportDao())
    }

    @After
    fun tearDown() = db.close()

    private fun link(shortcode: String) = "https://www.instagram.com/reel/$shortcode/"

    /** A desktop Post as the backup file writes it; [extra] overrides or adds fields. */
    private fun post(id: Long, shortcode: String, extra: String = "") =
        """{"id": $id, "link": "${link(shortcode)}", "collection_id": null, "title": "Title $shortcode",
            "title_manual": 0, "description": "Caption $shortcode", "note": null, "owner_name": null,
            "owner_username": null, "source": "instagram", "provenance": "instagram-import",
            "reimport_dismissed": 0, "created_at": 100, "updated_at": 200, "tag_ids": []
            ${if (extra.isEmpty()) "" else ", $extra"}}"""

    private fun backup(
        posts: List<String> = emptyList(),
        collections: String = "",
        tags: String = "",
        deletedPosts: String = "",
        version: Int = 6,
    ) = """{"schema_version": $version, "sections": [{"id": 1, "name": "Food", "created_at": 1, "updated_at": 1}],
        "collections": [$collections], "tags": [$tags], "posts": [${posts.joinToString()}],
        "deleted_posts": [$deletedPosts]}"""

    private fun collection(id: Long, name: String, color: String = "#B5533C", note: String? = null) =
        """{"id": $id, "name": "$name", "note": ${note?.let { "\"$it\"" }}, "color": "$color", "section_id": 1,
            "created_at": 1, "updated_at": 1}"""

    private fun tag(id: Long, name: String, color: String = "#B5533C") =
        """{"id": $id, "name": "$name", "color": "$color", "created_at": 1, "updated_at": 1}"""

    private suspend fun import(file: String): Summary = desktopImport.import(file.byteInputStream())

    private suspend fun held(): Backup = db.backupDao().read()

    private suspend fun stored(shortcode: String): Post = checkNotNull(posts.get(shortcode))

    private suspend fun tagNames(shortcode: String) = tags.observeOnPost(stored(shortcode).id).first().map { it.name }

    private suspend fun addPost(shortcode: String, collectionId: Long? = null): Long {
        val id = posts.insert(Post(shortcode = shortcode, url = "https://www.instagram.com/p/$shortcode/", addedAt = 1L))
        posts.setCollection(id, collectionId, at = 1L)
        return id
    }

    // The mapping (ADR-0009)

    @Test
    fun postsCarryOverWithTheirCollectionTagsAndNotes() = runTest {
        val summary = import(
            backup(
                collections = collection(7, "Recipes", color = "#6B7A4F", note = "Weeknights"),
                tags = "${tag(3, "Vegan", color = "#4C6B8A")}, ${tag(4, "Quick")}",
                posts = listOf(
                    post(
                        11,
                        "A",
                        """"collection_id": 7, "note": "Double the garlic", "owner_name": "Ann",
                            "owner_username": "ann.cooks", "tag_ids": [3, 4]""",
                    ),
                    post(12, "B"),
                ),
            ),
        )

        assertEquals(Summary(added = 2, completed = 0, skipped = 0), summary)
        val recipes = collections.observeAll().first().single().collection
        assertEquals(Triple("Recipes", PALETTE[2], "Weeknights"), Triple(recipes.name, recipes.color, recipes.note))
        assertEquals(listOf(PALETTE[0], PALETTE[1]), tags.observeAll().first().map { it.tag.color })
        val a = stored("A")
        assertEquals(link("A"), a.url)
        assertEquals(recipes.id, a.collectionId)
        assertEquals("Title A", a.title)
        assertEquals("Caption A", a.description)
        assertEquals("Double the garlic", a.postNote)
        assertEquals("Ann" to "ann.cooks", a.ownerName to a.ownerUsername)
        assertEquals(100L to 200L, a.addedAt to a.modifiedAt)
        assertEquals(listOf("Quick", "Vegan"), tagNames("A"))
        assertNull(stored("B").collectionId)
    }

    @Test
    fun everyImportedPostCountsAsDescriptionHandEdited() = runTest {
        import(backup(posts = listOf(post(1, "A"), post(2, "B", """"description": null"""))))

        assertTrue(stored("A").descriptionHandEdited)
        assertTrue(stored("B").descriptionHandEdited)
        assertEquals("", stored("B").description)
    }

    @Test
    fun aManualTitleStaysHandEdited() = runTest {
        import(backup(posts = listOf(post(1, "A", """"title": "Dal", "title_manual": 1"""), post(2, "B"))))

        assertEquals("Dal" to true, stored("A").title to stored("A").titleHandEdited)
        assertFalse(stored("B").titleHandEdited)
    }

    @Test
    fun onlyPostsFromAnInstagramImportCountAsSeenInAnExport() = runTest {
        import(
            backup(
                posts = listOf(
                    post(1, "A"),
                    post(2, "B", """"provenance": "manual""""),
                    post(3, "C", """"provenance": "bulk-paste""""),
                ),
            ),
        )

        assertTrue(stored("A").seenInExport)
        assertFalse(stored("B").seenInExport)
        assertFalse(stored("C").seenInExport)
    }

    @Test
    fun deletedPostsBecomeTraces() = runTest {
        import(backup(deletedPosts = """{"link": "${link("A")}?igsh=x", "title": "Gone", "deleted_at": 5}"""))

        assertTrue(deleted.hasTrace("A"))
        assertNull(posts.get("A"))
    }

    @Test
    fun linksWithoutAShortcodeAndRepeatedPostsAreSkipped() = runTest {
        val summary = import(
            backup(
                posts = listOf(
                    post(1, "A"),
                    post(2, "ignored", """"link": "https://www.instagram.com/p/A/?img_index=2""""),
                    post(3, "ignored", """"link": "https://www.instagram.com/some.profile/""""),
                ),
            ),
        )

        assertEquals(Summary(added = 1, completed = 0, skipped = 2), summary)
        assertEquals(listOf("A"), posts.observeAll().first().map { it.shortcode })
    }

    // Adding to what the app holds

    @Test
    fun aCollectionOrTagOfTheSameNameIsJoined() = runTest {
        val recipes = checkNotNull(collections.create("recipes", PALETTE[5]))
        val vegan = checkNotNull(tags.create("VEGAN", PALETTE[5]))

        import(
            backup(
                collections = collection(7, "Recipes", note = "Weeknights"),
                tags = tag(3, "Vegan"),
                posts = listOf(post(1, "A", """"collection_id": 7, "tag_ids": [3]""")),
            ),
        )

        val kept = collections.observeAll().first().single().collection
        assertEquals(Triple("recipes", PALETTE[5], "Weeknights"), Triple(kept.name, kept.color, kept.note))
        assertEquals(recipes, stored("A").collectionId)
        assertEquals(listOf(vegan), tags.observeAll().first().map { it.tag.id })
        assertEquals(listOf("VEGAN"), tagNames("A"))
    }

    @Test
    fun aPostAlreadyInTheAppOnlyGetsWhatItLacks() = runTest {
        val a = addPost("A")

        val summary = import(
            backup(
                collections = collection(7, "Recipes"),
                tags = tag(3, "Vegan"),
                posts = listOf(post(1, "A", """"collection_id": 7, "note": "Double the garlic", "tag_ids": [3]""")),
            ),
        )

        assertEquals(Summary(added = 0, completed = 1, skipped = 0), summary)
        val post = stored("A")
        assertEquals(a, post.id)
        assertEquals("https://www.instagram.com/p/A/", post.url)
        assertEquals(1L, post.addedAt)
        assertEquals("Recipes", collections.get(checkNotNull(post.collectionId))?.name)
        assertEquals("Caption A" to true, post.description to post.descriptionHandEdited)
        assertEquals("Title A", post.title)
        assertEquals("Double the garlic", post.postNote)
        assertTrue(post.seenInExport)
        assertEquals(listOf("Vegan"), tagNames("A"))
    }

    @Test
    fun whatTheUserSetInTheAppIsNeverReplaced() = runTest {
        val travel = checkNotNull(collections.create("Travel", PALETTE[0]))
        val a = addPost("A", travel)
        tags.addToPost(a, checkNotNull(tags.create("Later", PALETTE[0])), at = 2)
        posts.updateText(
            stored("A").copy(
                title = "Mine",
                titleHandEdited = true,
                description = "My words",
                descriptionHandEdited = true,
                postNote = "My note",
            ),
            at = 3,
        )
        val before = stored("A")

        val summary = import(
            backup(
                collections = collection(7, "Recipes"),
                tags = tag(3, "Vegan"),
                posts = listOf(
                    post(
                        1,
                        "A",
                        """"collection_id": 7, "note": "Desktop note", "tag_ids": [3], "title_manual": 1,
                            "provenance": "manual"""",
                    ),
                ),
            ),
        )

        assertEquals(Summary(added = 0, completed = 0, skipped = 0), summary)
        assertEquals(before, stored("A"))
        assertEquals(listOf("Later"), tagNames("A"))
    }

    @Test
    fun deletedPostsOfTheAppStayDeleted() = runTest {
        // A is in Recently deleted, B down to its trace.
        posts.delete(addPost("A"), at = 5)
        posts.delete(addPost("B"), at = 1)
        deleted.purge(deletedUpTo = 1)
        val before = held()

        val summary = import(backup(posts = listOf(post(1, "A"), post(2, "B"))))

        assertEquals(Summary(added = 0, completed = 0, skipped = 0), summary)
        assertEquals(before, held())
    }

    @Test
    fun anImportedTraceNeverDeletesAPostOfTheApp() = runTest {
        addPost("A")

        import(backup(deletedPosts = """{"link": "${link("A")}", "deleted_at": 5}"""))

        assertFalse(deleted.hasTrace("A"))
        assertEquals(listOf("A"), posts.observeAll().first().map { it.shortcode })
    }

    @Test
    fun importingTheSameFileTwiceChangesNothing() = runTest {
        val file = backup(
            collections = collection(7, "Recipes", note = "Weeknights"),
            tags = tag(3, "Vegan"),
            posts = listOf(post(1, "A", """"collection_id": 7, "tag_ids": [3]"""), post(2, "B")),
            deletedPosts = """{"link": "${link("C")}", "deleted_at": 5}""",
        )
        import(file)
        val after = held()

        val summary = import(file)

        assertEquals(Summary(added = 0, completed = 0, skipped = 0), summary)
        assertEquals(after, held())
    }

    // Files that are refused

    @Test
    fun aFileThatIsNotADesktopV6BackupChangesNothing() = runTest {
        addPost("A")
        val before = held()

        listOf(
            "not json",
            "[]",
            """{"app": "instasaved", "version": 1}""",
            backup(posts = listOf(post(1, "B")), version = 5),
            backup(posts = listOf("""{"id": 1}""")),
            """{"schema_version": 6, "collections": []}""",
        ).forEach { file ->
            assertThrows(DesktopBackupFormatException::class.java) { parseDesktopBackup(file) }
            try {
                import(file)
            } catch (_: DesktopBackupFormatException) {
            }
        }

        assertEquals(before, held())
    }
}

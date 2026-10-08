package com.maxlutz.instasaved.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.maxlutz.instasaved.sync.ExportedPost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Builds each old database from its exported schema in `app/schemas`, then lets Room migrate and open it.
 * Room validates the migrated tables against the current entities on open, so a wrong migration fails here.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"

    @Before
    @After
    fun deleteDatabase() {
        context.deleteDatabase(dbName)
    }

    private fun createDatabase(version: Int, seed: List<String>) {
        val schema = JSONObject(File("schemas/${AppDatabase::class.java.name}/$version.json").readText())
            .getJSONObject("database")
        context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            seed.forEach(db::execSQL)
            db.version = version
        }
    }

    private fun openMigrated() = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .addMigrations(*AppDatabase.MIGRATIONS)
        .allowMainThreadQueries()
        .build()

    @Test
    fun migrates1To2KeepingPostsByShortcode() = runTest {
        createDatabase(1, seed = listOf("INSERT INTO posts (shortcode) VALUES ('ABC123')"))

        val db = openMigrated()
        val post = db.postDao().get("ABC123")!!
        db.close()

        assertEquals("https://www.instagram.com/p/ABC123/", post.url)
        assertFalse(post.seenInExport)
    }

    @Test
    fun migrates2To3WithEmptyTextNothingHandEditedAndNotDeleted() = runTest {
        createDatabase(
            2,
            seed = listOf(
                "INSERT INTO posts (shortcode, url, addedAt, seenInExport) " +
                    "VALUES ('ABC123', 'https://www.instagram.com/reel/ABC123/', 7, 1)",
            ),
        )

        val db = openMigrated()
        val post = db.postDao().get("ABC123")!!
        db.close()

        assertEquals(
            Post(id = post.id, shortcode = "ABC123", url = "https://www.instagram.com/reel/ABC123/", addedAt = 7, seenInExport = true),
            post,
        )
    }

    @Test
    fun migrates3To4WithEveryPostInToSortAndWorkingCollections() = runTest {
        createDatabase(
            3,
            seed = listOf(
                "INSERT INTO posts (shortcode, url, addedAt, seenInExport, title, titleHandEdited, description, " +
                    "descriptionHandEdited, postNote) VALUES ('ABC123', 'https://www.instagram.com/p/ABC123/', 7, 0, " +
                    "'Hi', 0, 'Hi. There', 1, 'note')",
            ),
        )

        val db = openMigrated()
        val post = db.postDao().get("ABC123")!!
        assertEquals(listOf(post), db.postDao().observeToSort().first())
        // The migrated reference still sends Posts to To sort when their Collection is deleted.
        val recipes = db.collectionDao().create("Recipes", PALETTE[0])!!
        db.postDao().setCollection(post.id, recipes, at = 7L)
        db.collectionDao().deleteKeepingPosts(recipes)
        val after = db.postDao().get("ABC123")!!
        db.close()

        assertNull(post.collectionId)
        assertNull(post.deletionId)
        assertEquals("note", post.postNote)
        assertEquals(post, after)
    }

    @Test
    fun migrates4To5WithNoTagsAndWorkingTagging() = runTest {
        createDatabase(
            4,
            seed = listOf(
                "INSERT INTO posts (shortcode, url, addedAt, seenInExport, title, titleHandEdited, description, " +
                    "descriptionHandEdited, postNote) VALUES ('ABC123', 'https://www.instagram.com/p/ABC123/', 7, 0, " +
                    "'Hi', 0, 'Hi. There', 1, 'note')",
            ),
        )

        val db = openMigrated()
        val post = db.postDao().get("ABC123")!!
        val tagsBefore = db.tagDao().observeOnPost(post.id).first()
        val vegan = db.tagDao().create("Vegan", PALETTE[0])!!
        db.tagDao().addToPost(post.id, vegan, at = 1L)
        val tagsAfter = db.tagDao().observeOnPost(post.id).first().map { it.name }
        db.close()

        assertEquals(emptyList<Tag>(), tagsBefore)
        assertEquals(listOf("Vegan"), tagsAfter)
        assertEquals("note", post.postNote)
    }

    @Test
    fun migrates5To6WithNoOwnerAndEveryThumbnailStillToTry() = runTest {
        createDatabase(
            5,
            seed = listOf(
                "INSERT INTO posts (shortcode, url, addedAt, seenInExport, title, titleHandEdited, description, " +
                    "descriptionHandEdited, postNote) VALUES ('ABC123', 'https://www.instagram.com/p/ABC123/', 7, 0, " +
                    "'Hi', 0, 'Hi. There', 1, 'note')",
            ),
        )

        val db = openMigrated()
        val post = db.postDao().get("ABC123")!!
        db.postDao().thumbnailFailed(post.id, at = 9)
        val failed = db.postDao().getAllFewestThumbnailFailuresFirst().single()
        db.close()

        assertEquals("", post.ownerUsername)
        assertEquals("", post.ownerName)
        assertEquals("note", post.postNote)
        assertEquals(0, post.thumbnailFailures)
        assertNull(post.thumbnailFailedAt)
        assertEquals(post.copy(thumbnailFailures = 1, thumbnailFailedAt = 9), failed)
    }

    @Test
    fun migrates6To7KeepingDeletedPostsInRecentlyDeletedWithWorkingTraces() = runTest {
        createDatabase(
            6,
            seed = listOf(
                "INSERT INTO posts (shortcode, url, addedAt, seenInExport, title, titleHandEdited, description, " +
                    "descriptionHandEdited, postNote, deletedAt, ownerUsername, ownerName, thumbnailFailures) VALUES " +
                    "('ABC123', 'https://www.instagram.com/p/ABC123/', 7, 0, 'Hi', 0, 'Hi. There', 1, 'note', 9, '', '', 0)",
            ),
        )

        val db = openMigrated()
        val deleted = db.recentlyDeletedDao().observe().first()
        val traceBefore = db.recentlyDeletedDao().hasTrace("ABC123")
        db.recentlyDeletedDao().purge(deletedUpTo = 9)
        val traceAfter = db.recentlyDeletedDao().hasTrace("ABC123")
        val postAfter = db.postDao().get("ABC123")
        db.close()

        assertEquals(listOf("note"), deleted.map { it.postNote })
        assertFalse(traceBefore)
        assertEquals(true, traceAfter)
        assertNull(postAfter)
    }

    @Test
    fun migrates7To8WithEveryPostModifiedWhenItWasAdded() = runTest {
        createDatabase(
            7,
            seed = listOf("A" to 7, "B" to 9).map { (shortcode, addedAt) ->
                "INSERT INTO posts (shortcode, url, addedAt, seenInExport, title, titleHandEdited, description, " +
                    "descriptionHandEdited, postNote, ownerUsername, ownerName, thumbnailFailures) VALUES " +
                    "('$shortcode', 'https://www.instagram.com/p/$shortcode/', $addedAt, 0, 'Hi', 0, 'Hi. There', 1, " +
                    "'note', '', '', 0)"
            },
        )

        val db = openMigrated()
        val before = db.postDao().observeAll().first()
        db.postDao().updateText(before.first().editPostNote("new note"), at = 12)
        val after = db.postDao().observeAll().first()
        db.close()

        assertEquals(listOf("B" to 9L, "A" to 7L), before.map { it.shortcode to it.modifiedAt })
        assertEquals(listOf("B" to 12L, "A" to 7L), after.map { it.shortcode to it.modifiedAt })
        assertEquals(listOf("new note", "note"), after.map { it.postNote })
    }

    @Test
    fun migrates8To9WithNoSectionAndEveryCollectionAsItWas() = runTest {
        createDatabase(
            8,
            seed = listOf(
                "INSERT INTO collections (id, name, color, note) VALUES (1, 'Recipes', 7, 'Weeknights')",
                "INSERT INTO collection_deletions (id, collectionName, collectionColor, collectionNote) " +
                    "VALUES (1, 'Travel', 3, '')",
                "INSERT INTO posts (shortcode, url, addedAt, modifiedAt, seenInExport, title, titleHandEdited, " +
                    "description, descriptionHandEdited, postNote, ownerUsername, ownerName, thumbnailFailures, " +
                    "collectionId) VALUES ('A', 'https://www.instagram.com/p/A/', 7, 7, 0, '', 0, '', 0, '', '', '', 0, 1)",
            ),
        )

        val db = openMigrated()
        val sectionsBefore = db.sectionDao().observeAll().first()
        val before = db.collectionDao().get(1)
        val deletion = db.collectionDao().getDeletion(1)
        val food = checkNotNull(db.sectionDao().create("Food"))
        db.collectionDao().update(checkNotNull(before).copy(sectionId = food))
        val after = db.collectionDao().get(1)
        val inCollection = db.postDao().observeInCollection(1).first().map { it.shortcode }
        db.close()

        assertEquals(emptyList<Section>(), sectionsBefore)
        assertEquals(Collection(1, "Recipes", 7, "Weeknights", sectionId = null), before)
        assertNull(deletion?.collectionSectionId)
        assertEquals(food, after?.sectionId)
        assertEquals(listOf("A"), inCollection)
    }

    @Test
    fun migrates9To10WithNoExportAppliedAndNoInstagramCollectionsKnown() = runTest {
        createDatabase(
            9,
            seed = listOf(
                "INSERT INTO posts (shortcode, url, addedAt, modifiedAt, seenInExport, title, titleHandEdited, " +
                    "description, descriptionHandEdited, postNote, ownerUsername, ownerName, thumbnailFailures) " +
                    "VALUES ('A', 'https://www.instagram.com/p/A/', 7, 8, 1, 'Hi', 0, 'Hi', 0, '', '', '', 0)",
            ),
        )

        val db = openMigrated()
        val before = db.postDao().get("A")
        val applied = db.syncDao().appliedExportIds()
        db.syncDao().apply(AppliedExport("E", "instagram-someone-2026-10-01-x", "2026-10-01", 9), emptyList())
        val after = db.syncDao().appliedExportIds()
        db.close()

        assertEquals(emptyList<String>(), before?.instagramCollections)
        assertEquals(7L to 8L, before?.addedAt to before?.modifiedAt)
        assertEquals(emptyList<String>(), applied)
        assertEquals(listOf("E"), after)
    }

    @Test
    fun migrates10To11WithNoPostNew() = runTest {
        createDatabase(
            10,
            seed = listOf(
                "INSERT INTO posts (shortcode, url, addedAt, modifiedAt, seenInExport, title, titleHandEdited, " +
                    "description, descriptionHandEdited, postNote, ownerUsername, ownerName, thumbnailFailures, " +
                    "instagramCollections) " +
                    "VALUES ('A', 'https://www.instagram.com/p/A/', 7, 8, 1, 'Hi', 0, 'Hi', 0, '', '', '', 0, '')",
            ),
        )

        val db = openMigrated()
        val before = db.postDao().get("A")
        db.syncDao().apply(
            AppliedExport("E", "instagram-someone-2026-10-01-x", "2026-10-01", 9),
            listOf(ExportedPost("B", "https://www.instagram.com/p/B/", null, null, null, 5, emptyList())),
        )
        val added = db.postDao().get("B")
        db.close()

        assertEquals(false, before?.isNew)
        assertEquals(7L to 8L, before?.addedAt to before?.modifiedAt)
        assertEquals(true, added?.isNew)
    }
}

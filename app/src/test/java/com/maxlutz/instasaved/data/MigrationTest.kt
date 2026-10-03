package com.maxlutz.instasaved.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
        db.postDao().setCollection(post.id, recipes)
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
        db.tagDao().addToPost(post.id, vegan)
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
}

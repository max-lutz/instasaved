package com.maxlutz.instasaved.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            seed.forEach(db::execSQL)
            db.version = version
        }
    }

    private fun openMigrated() = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
        .addMigrations(MIGRATION_1_2)
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
}

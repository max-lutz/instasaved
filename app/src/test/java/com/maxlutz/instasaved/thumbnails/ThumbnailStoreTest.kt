package com.maxlutz.instasaved.thumbnails

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ThumbnailStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val jpeg = byteArrayOf(1, 2, 3)

    @Test
    fun aSavedThumbnailIsKeptAsItsImageBytes() {
        val store = ThumbnailStore(folder.root)

        store.save("ABC123", jpeg)

        assertTrue(store.has("ABC123"))
        assertEquals(setOf("ABC123"), store.shortcodes.value)
        assertArrayEquals(jpeg, store.file("ABC123").readBytes())
    }

    @Test
    fun aPostWithoutThumbnailHasNone() {
        val store = ThumbnailStore(File(folder.root, "not-created-yet"))

        assertFalse(store.has("ABC123"))
        assertEquals(emptySet<String>(), store.shortcodes.value)
    }

    @Test
    fun shortcodesDifferingOnlyByCaseAreDifferentPosts() {
        val store = ThumbnailStore(folder.root)

        store.save("AbC", jpeg)

        assertFalse(store.has("aBc"))
    }

    @Test
    fun thumbnailsSavedEarlierAreFoundAgain() {
        ThumbnailStore(folder.root).save("ABC123", jpeg)

        assertEquals(setOf("ABC123"), ThumbnailStore(folder.root).shortcodes.value)
    }

    @Test
    fun anInterruptedSaveIsNotAThumbnail() {
        File(folder.root, "ABC123.part").writeBytes(jpeg)

        assertFalse(ThumbnailStore(folder.root).has("ABC123"))
    }

    @Test
    fun thumbnailsAreStoredWhereAndroidBackupNeverLooks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ThumbnailStore(context)

        store.save("ABC123", jpeg)

        assertTrue(store.file("ABC123").startsWith(context.noBackupFilesDir))
    }
}

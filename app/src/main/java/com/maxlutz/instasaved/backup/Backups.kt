package com.maxlutz.instasaved.backup

import android.database.sqlite.SQLiteConstraintException
import com.maxlutz.instasaved.data.BackupDao
import com.maxlutz.instasaved.thumbnails.ThumbnailStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/** The manual backup file: the second safety net next to Android's automatic backup (ADR-0001). */
class Backups(
    private val dao: BackupDao,
    private val thumbnails: ThumbnailStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Writes a Backup of everything the app holds to [out], and closes it. */
    suspend fun write(out: OutputStream) {
        val json = dao.read().toJson(createdAt = now())
        withContext(Dispatchers.IO) { out.use { it.write(json.toByteArray()) } }
    }

    /**
     * Wipe-and-replace: the app's data becomes what the backup file read from [input] holds, and [input] is closed.
     * Thumbnails of Posts the Backup does not have go too.
     *
     * @throws BackupFormatException when the file is not a Backup this app can restore; the app's data is then
     *   left as it was.
     */
    suspend fun restore(input: InputStream) {
        val backup = parseBackup(withContext(Dispatchers.IO) { input.use { it.readBytes() } }.decodeToString())
        try {
            dao.replaceAll(backup)
        } catch (e: SQLiteConstraintException) {
            throw BackupFormatException("The backup file does not hold together", e)
        }
        val kept = backup.posts.mapTo(HashSet()) { it.shortcode }
        (thumbnails.shortcodes.value - kept).forEach(thumbnails::delete)
    }
}

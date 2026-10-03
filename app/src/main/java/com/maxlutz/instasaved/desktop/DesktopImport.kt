package com.maxlutz.instasaved.desktop

import com.maxlutz.instasaved.data.DesktopImportDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/** The one-time Desktop Import of a Socials Organizer backup file (ADR-0009). */
class DesktopImport(private val dao: DesktopImportDao) {
    /**
     * @property added Posts the app did not have.
     * @property completed Posts the app already had, that got something they lacked.
     * @property skipped desktop Posts left out: no Instagram post link, or the same post twice.
     */
    data class Summary(val added: Int, val completed: Int, val skipped: Int)

    /**
     * Adds what the desktop backup read from [input] holds to the app, and closes [input]. Nothing the app
     * already has is replaced, so importing the same file again changes nothing.
     *
     * @throws DesktopBackupFormatException when the file is not a Socials Organizer v6 backup; the app's data is
     *   then left as it was.
     */
    suspend fun import(input: InputStream): Summary {
        val backup = parseDesktopBackup(withContext(Dispatchers.IO) { input.use { it.readBytes() } }.decodeToString())
        val imported = dao.add(backup.data)
        return Summary(imported.added, imported.completed, backup.skippedPosts)
    }
}

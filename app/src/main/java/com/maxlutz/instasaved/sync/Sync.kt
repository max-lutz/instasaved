package com.maxlutz.instasaved.sync

import com.maxlutz.instasaved.data.AppliedExport
import com.maxlutz.instasaved.data.SyncDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/**
 * Sync's pipeline (`docs/sync-spec.md`): finds the Exports in Drive not applied yet and applies each, oldest first,
 * in a transaction of its own, then records the sync status and the Sync Summary, and queues the Thumbnails of the
 * Posts it added.
 *
 * @param queueThumbnails starts downloading the Thumbnails the app is missing, once the Exports are applied.
 */
class Sync(
    private val dao: SyncDao,
    private val status: SyncStatusStore,
    private val queueThumbnails: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * What a Sync did.
     *
     * @property summary the Sync Summary, totalled over the Exports applied.
     * @property problem what went wrong, as the sync status now says it.
     */
    data class Report(val summary: SyncSummary, val problem: SyncProblem?)

    // One Sync at a time: two would apply the same Export twice.
    private val lock = Mutex()
    private val running = MutableStateFlow(false)

    /** Whether a Sync is under way. */
    val isRunning: StateFlow<Boolean> = running.asStateFlow()

    /** Applies the Exports in [drive] that the app has not applied yet. */
    suspend fun run(drive: Drive): Report = lock.withLock {
        running.value = true
        try {
            applyNewExports(drive)
        } finally {
            running.value = false
        }
    }

    /** Records that a Sync could not start, for a reason outside Drive's Exports (e.g. no access granted). */
    fun failed(problem: SyncProblem): Report {
        status.failed(problem)
        return Report(SyncSummary(), problem)
    }

    private suspend fun applyNewExports(drive: Drive): Report {
        val exports = try {
            drive.exports()
        } catch (_: DriveAccessException) {
            return failed(SyncProblem.AccessRefused)
        } catch (_: IOException) {
            return failed(SyncProblem.Offline)
        }
        if (exports.isEmpty()) return failed(SyncProblem.NoExport)

        val applied = dao.appliedExportIds().toSet()
        var summary = SyncSummary()
        val failedDates = mutableListOf<LocalDate>()
        for (export in exports.filter { it.id !in applied }.sortedWith(oldestFirst)) {
            try {
                val result = dao.apply(export.record(), read(drive, export))
                summary = SyncSummary(
                    new = summary.new + result.summary.new,
                    captionsUpdated = summary.captionsUpdated + result.summary.captionsUpdated,
                )
            } catch (_: DriveAccessException) {
                // The next Exports would be refused too; those applied so far stay applied.
                if (summary.new > 0) queueThumbnails()
                status.summarize(summary)
                return failed(SyncProblem.AccessRefused)
            } catch (_: IOException) {
                failedDates += export.date
            } catch (_: ExportFormatException) {
                failedDates += export.date
            }
        }
        if (summary.new > 0) queueThumbnails()
        val problem = failedDates.takeIf { it.isNotEmpty() }?.let(SyncProblem::ExportsFailed)
        status.synced(
            at = now(),
            newestExport = dao.newestExportDate()?.let(LocalDate::parse),
            newestInDrive = exports.maxOf { it.date },
            problem = problem,
        )
        status.summarize(summary)
        return Report(summary, problem)
    }

    /** The Export's posts, downloaded and parsed. */
    private suspend fun read(drive: Drive, export: DriveExport): List<ExportedPost> {
        // Not "no posts": Meta may still be writing the folder, and the next Sync will find the file.
        val savedPosts = drive.read(export, SAVED_POSTS_PATH)
            ?: throw ExportFormatException("${export.name} has no $SAVED_POSTS_PATH")
        val savedCollections = drive.read(export, SAVED_COLLECTIONS_PATH)
        return withContext(Dispatchers.Default) { parseExport(savedPosts, savedCollections) }
    }

    private fun DriveExport.record() = AppliedExport(id, name, date.toString(), appliedAt = now())
}

package com.maxlutz.instasaved.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/** Why the last Sync did not apply everything it should have. */
sealed interface SyncProblem {
    /** The user did not give the app access to Google Drive. */
    data object AccessNotGranted : SyncProblem

    /** Google Drive refused the app's access: it was revoked, or the sign-in expired. */
    data object AccessRefused : SyncProblem

    /** Google Drive could not be reached. */
    data object Offline : SyncProblem

    /** Google Drive has no Export: the Instagram schedule is not set up, or writes elsewhere. */
    data object NoExport : SyncProblem

    /** These Exports could not be downloaded or read; the next Sync tries them again. */
    data class ExportsFailed(val dates: List<LocalDate>) : SyncProblem
}

/**
 * What the sync status line says (sync-spec "Sync status and Summary").
 *
 * @property syncedAt when a Sync last went through Drive's Exports, in epoch milliseconds; null if none ever did.
 * @property newestExport the day of the newest Export applied.
 * @property problem what went wrong in the last Sync, if anything did.
 */
data class SyncStatus(
    val syncedAt: Long? = null,
    val newestExport: LocalDate? = null,
    val problem: SyncProblem? = null,
)

/** The sync status, kept in [prefs] across launches. */
class SyncStatusStore(private val prefs: SharedPreferences) {
    private val state = MutableStateFlow(load())

    val status: StateFlow<SyncStatus> = state.asStateFlow()

    /** A Sync went through the Exports: the status line says when, and what went wrong with some of them. */
    fun synced(at: Long, newestExport: LocalDate?, problem: SyncProblem?) =
        save(SyncStatus(at, newestExport, problem))

    /** A Sync stopped before going through the Exports: the status line keeps the last one that did. */
    fun failed(problem: SyncProblem) = save(state.value.copy(problem = problem))

    private fun save(status: SyncStatus) {
        prefs.edit {
            putOrRemove(SYNCED_AT, status.syncedAt)
            putOrRemove(NEWEST_EXPORT, status.newestExport?.toString())
            putOrRemove(PROBLEM, status.problem?.let(::encode))
        }
        state.value = status
    }

    private fun load() = SyncStatus(
        syncedAt = prefs.getLong(SYNCED_AT, -1).takeIf { it >= 0 },
        newestExport = prefs.getString(NEWEST_EXPORT, null)?.let(LocalDate::parse),
        problem = prefs.getString(PROBLEM, null)?.let(::decode),
    )

    private fun encode(problem: SyncProblem): String = when (problem) {
        SyncProblem.AccessNotGranted -> "access-not-granted"
        SyncProblem.AccessRefused -> "access-refused"
        SyncProblem.Offline -> "offline"
        SyncProblem.NoExport -> "no-export"
        is SyncProblem.ExportsFailed -> "exports-failed:" + problem.dates.joinToString(",")
    }

    // An unknown value comes from a newer app, restored from its backup: better no problem than a wrong one.
    private fun decode(value: String): SyncProblem? = when {
        value == "access-not-granted" -> SyncProblem.AccessNotGranted
        value == "access-refused" -> SyncProblem.AccessRefused
        value == "offline" -> SyncProblem.Offline
        value == "no-export" -> SyncProblem.NoExport
        value.startsWith("exports-failed:") -> SyncProblem.ExportsFailed(
            value.removePrefix("exports-failed:").split(',').filter { it.isNotEmpty() }.map(LocalDate::parse),
        )
        else -> null
    }

    private companion object {
        const val SYNCED_AT = "syncedAt"
        const val NEWEST_EXPORT = "newestExport"
        const val PROBLEM = "problem"
    }
}

private fun SharedPreferences.Editor.putOrRemove(key: String, value: Long?) {
    if (value == null) remove(key) else putLong(key, value)
}

private fun SharedPreferences.Editor.putOrRemove(key: String, value: String?) {
    if (value == null) remove(key) else putString(key, value)
}

package com.maxlutz.instasaved.thumbnails

import android.content.SharedPreferences
import androidx.core.content.edit

/** How the last runs of [ThumbnailDownloader] ended. Kept across restarts of the app: the waits last hours. */
class ThumbnailRuns(private val preferences: SharedPreferences) {
    /** How many runs in a row stopped early, with no download working since. */
    val stoppedEarlyInARow: Int get() = preferences.getInt(STOPPED_EARLY_IN_A_ROW, 0)

    /** No run before then, in epoch milliseconds; 0 when nothing holds the runs back. */
    val heldBackUntil: Long get() = preferences.getLong(HELD_BACK_UNTIL, 0)

    fun stoppedEarly(inARow: Int, heldBackUntil: Long) = preferences.edit {
        putInt(STOPPED_EARLY_IN_A_ROW, inARow)
        putLong(HELD_BACK_UNTIL, heldBackUntil)
    }

    /** A download worked: the next run that stops early waits the shortest time again. */
    fun downloadWorked() {
        if (stoppedEarlyInARow != 0) preferences.edit { putInt(STOPPED_EARLY_IN_A_ROW, 0) }
    }

    private companion object {
        const val STOPPED_EARLY_IN_A_ROW = "stoppedEarlyInARow"
        const val HELD_BACK_UNTIL = "heldBackUntil"
    }
}

package com.maxlutz.instasaved.more

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the user set on the More screen, kept in [prefs] across launches. Not part of a Backup file. */
class Settings(private val prefs: SharedPreferences) {
    private val showBarePostsState = MutableStateFlow(prefs.getBoolean(SHOW_BARE_POSTS, false))

    /** Whether the covers of the Saved screen say how many of their Posts are Bare Posts. Off until turned on. */
    val showBarePosts: StateFlow<Boolean> = showBarePostsState.asStateFlow()

    fun setShowBarePosts(show: Boolean) {
        prefs.edit { putBoolean(SHOW_BARE_POSTS, show) }
        showBarePostsState.value = show
    }

    private companion object {
        const val SHOW_BARE_POSTS = "showBarePosts"
    }
}

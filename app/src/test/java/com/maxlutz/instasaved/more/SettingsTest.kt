package com.maxlutz.instasaved.more

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // A new one each time, as on a launch of the app.
    private fun settings() = Settings(context.getSharedPreferences("settings-test", Context.MODE_PRIVATE))

    @Test
    fun barePostsAreNotShownUntilTurnedOn() {
        assertFalse(settings().showBarePosts.value)
    }

    @Test
    fun showingBarePostsIsKeptAcrossLaunches() {
        val settings = settings()

        settings.setShowBarePosts(true)

        assertTrue(settings.showBarePosts.value)
        assertTrue(settings().showBarePosts.value)
    }

    @Test
    fun showingBarePostsCanBeTurnedOffAgain() {
        settings().setShowBarePosts(true)

        settings().setShowBarePosts(false)

        assertFalse(settings().showBarePosts.value)
    }
}

package com.maxlutz.instasaved.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.maxlutz.instasaved.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/** Android's automatic backup takes the database and the settings, never the Thumbnails (ADR-0001). */
@RunWith(RobolectricTestRunner::class)
class AutomaticBackupRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Each rule as "section/tag domain path". */
    private fun rules(xml: Int): List<String> {
        val parser = context.resources.getXml(xml)
        val found = mutableListOf<String>()
        var section = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "include", "exclude" -> found +=
                    "$section/${parser.name} ${parser.getAttributeValue(null, "domain")} " +
                    parser.getAttributeValue(null, "path")
                else -> section = parser.name
            }
        }
        return found
    }

    @Test
    fun upToAndroid11OnlyTheDatabaseAndSettingsAreBackedUp() {
        assertEquals(
            listOf("full-backup-content/include database .", "full-backup-content/include sharedpref ."),
            rules(R.xml.backup_rules),
        )
    }

    @Test
    fun fromAndroid12OnlyTheDatabaseAndSettingsAreBackedUpOrTransferred() {
        assertEquals(
            listOf(
                "cloud-backup/include database .",
                "cloud-backup/include sharedpref .",
                "device-transfer/include database .",
                "device-transfer/include sharedpref .",
            ),
            rules(R.xml.data_extraction_rules),
        )
    }
}

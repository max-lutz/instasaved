package com.maxlutz.instasaved.sync

import com.maxlutz.instasaved.thumbnails.Http
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.URLDecoder
import java.time.LocalDate

class DriveRestTest {
    /** Answers a files.list with the files whose query it was given, and a download with the file's content. */
    private class FakeApi : Http {
        val lists = mutableMapOf<String, List<String>>()
        val contents = mutableMapOf<String, String>()
        val requests = mutableListOf<String>()
        var status = 200

        override fun get(url: String): Http.Response {
            requests += url
            if (status != 200) return Http.Response(status, "", ByteArray(0))
            val path = url.substringBefore('?')
            val params = url.substringAfter('?', "").split('&').filter { it.isNotEmpty() }
                .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
            if (path.endsWith("/files")) {
                val pages = lists[params.getValue("q")] ?: listOf("""{"files": []}""")
                val page = params["pageToken"]?.toInt() ?: 0
                return Http.Response(200, "application/json", pages[page].toByteArray())
            }
            val content = contents[path.substringAfterLast('/')] ?: return Http.Response(404, "", ByteArray(0))
            return Http.Response(200, "application/json", content.toByteArray())
        }
    }

    private val api = FakeApi()
    private val drive = DriveRest(api)
    private val exportsQuery =
        "mimeType = 'application/vnd.google-apps.folder' and name contains 'instagram' and trashed = false"

    private fun file(id: String, name: String, createdTime: String = "2026-10-01T03:00:00Z") =
        """{"id": "$id", "name": "$name", "createdTime": "$createdTime"}"""

    private fun child(parent: String, name: String, folder: Boolean) =
        "'$parent' in parents and name = '$name' and " +
            (if (folder) "mimeType = " else "mimeType != ") + "'application/vnd.google-apps.folder' and trashed = false"

    private val export = DriveExport("E", "instagram-someone-2026-10-01-UNXRcIvM", LocalDate.of(2026, 10, 1), "")

    @Test
    fun findsExportFoldersByNameOnly() = runTest {
        api.lists[exportsQuery] = listOf(
            """{"files": [${file("1", "instagram-someone-2026-10-01-UNXRcIvM")},
                ${file("2", "instagram photos")}, ${file("3", "instagram-someone-2026-13-01-UNXRcIvM")}],
                "nextPageToken": "1"}""",
            """{"files": [${file("4", "instagram-some_one.2-2026-10-02-0bRMehPh", "2026-10-02T03:00:00Z")}]}""",
        )

        assertEquals(
            listOf(
                DriveExport("1", "instagram-someone-2026-10-01-UNXRcIvM", LocalDate.of(2026, 10, 1), "2026-10-01T03:00:00Z"),
                DriveExport(
                    "4",
                    "instagram-some_one.2-2026-10-02-0bRMehPh",
                    LocalDate.of(2026, 10, 2),
                    "2026-10-02T03:00:00Z",
                ),
            ),
            drive.exports(),
        )
    }

    @Test
    fun readsAFileByItsPathInTheExport() = runTest {
        api.lists[child("E", "your_instagram_activity", folder = true)] = listOf("""{"files": [${file("F1", "")}]}""")
        api.lists[child("F1", "saved", folder = true)] = listOf("""{"files": [${file("F2", "")}]}""")
        api.lists[child("F2", "saved_posts.json", folder = false)] = listOf("""{"files": [${file("P", "")}]}""")
        api.contents["P"] = "[]"

        assertEquals("[]", drive.read(export, SAVED_POSTS_PATH))
        assertEquals("https://www.googleapis.com/drive/v3/files/P?alt=media", api.requests.last())
    }

    @Test
    fun aFileTheExportDoesNotHaveIsNull() = runTest {
        api.lists[child("E", "your_instagram_activity", folder = true)] = listOf("""{"files": [${file("F1", "")}]}""")
        api.lists[child("F1", "saved", folder = true)] = listOf("""{"files": [${file("F2", "")}]}""")

        assertNull(drive.read(export, SAVED_COLLECTIONS_PATH))
    }

    @Test
    fun aRefusedAccessIsToldApartFromOtherFailures() {
        api.status = 401
        assertThrows(DriveAccessException::class.java) { runTest { drive.exports() } }
        api.status = 403
        assertThrows(DriveAccessException::class.java) { runTest { drive.exports() } }
        api.status = 500
        val other = assertThrows(IOException::class.java) { runTest { drive.exports() } }
        assertEquals(IOException::class.java, other.javaClass)
    }

    @Test
    fun anAnswerThatIsNotJsonIsAFailure() {
        api.lists[exportsQuery] = listOf("<html>")
        assertThrows(IOException::class.java) { runTest { drive.exports() } }
    }

    @Test
    fun readsTheDayFromAnExportsName() {
        assertEquals(LocalDate.of(2026, 10, 3), exportDate("instagram-someone-2026-10-03-P6s1Q95H"))
        assertNull(exportDate("instagram-someone-2026-02-30-P6s1Q95H"))
        assertNull(exportDate("meta-2026-Oct-01-10-39-52"))
        assertNull(exportDate("instagram-someone-2026-10-03"))
    }

    @Test
    fun ordersExportsOldestFirst() {
        val a = DriveExport("a", "x", LocalDate.of(2026, 10, 2), "2026-10-02T01:00:00Z")
        val b = DriveExport("b", "x", LocalDate.of(2026, 10, 1), "2026-10-03T01:00:00Z")
        val c = DriveExport("c", "x", LocalDate.of(2026, 10, 2), "2026-10-02T00:00:00Z")

        assertEquals(listOf(b, c, a), listOf(a, b, c).sortedWith(oldestFirst))
    }
}

package com.maxlutz.instasaved.thumbnails

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * The Thumbnails on the phone: one image file per Post, named by its shortcode (ADR-0007). The files are
 * the only record of which Posts have a Thumbnail, so a restored Backup (which never has them) just
 * finds every Post without one.
 */
class ThumbnailStore(private val directory: File) {
    /** In the directory Android leaves out of its automatic backup (ADR-0001, ADR-0008). */
    constructor(context: Context) : this(File(context.noBackupFilesDir, "thumbnails"))

    private val saved = MutableStateFlow(directory.list().orEmpty().filterNot { it.endsWith(PARTIAL) }.toSet())

    /** The shortcodes of the Posts that have a Thumbnail. */
    val shortcodes: StateFlow<Set<String>> = saved

    fun has(shortcode: String): Boolean = shortcode in saved.value

    fun file(shortcode: String): File = File(directory, shortcode)

    fun save(shortcode: String, image: ByteArray) {
        directory.mkdirs()
        // Written aside, then renamed: a crash never leaves half an image under the Post's name.
        val partial = File(directory, shortcode + PARTIAL)
        partial.writeBytes(image)
        check(partial.renameTo(file(shortcode))) { "Could not save the Thumbnail of $shortcode" }
        saved.update { it + shortcode }
    }

    /** Removes the Post's Thumbnail, if it has one. */
    fun delete(shortcode: String) {
        file(shortcode).delete()
        saved.update { it - shortcode }
    }

    private companion object {
        // Shortcodes never contain a dot.
        const val PARTIAL = ".part"
    }
}

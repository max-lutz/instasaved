package com.maxlutz.instasaved.data

/** Longest auto-derived Title, ellipsis included. A hand-edited Title has no limit. */
const val TITLE_MAX_LENGTH = 80

// A run of sentence punctuation followed by whitespace or the end, so "2.0" or "example.com" don't end a sentence.
private val sentenceEnd = Regex("""[.!?…]+(?=\s|$)""")

/**
 * The Title a Post gets from its [description]: the first sentence of its first non-blank line.
 * A lone full stop is dropped; "!", "?" and ellipses are kept. Too long a sentence is cut at a word.
 */
fun titleFrom(description: String): String {
    val line = description.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
    val end = sentenceEnd.find(line)
    val sentence = when {
        end == null -> line
        end.value == "." -> line.substring(0, end.range.first)
        else -> line.substring(0, end.range.last + 1)
    }.trimEnd()
    if (sentence.length <= TITLE_MAX_LENGTH) return sentence
    val cut = sentence.substring(0, TITLE_MAX_LENGTH - 1)
    return cut.substringBeforeLast(' ', cut).trimEnd() + "…"
}

/** Hand-edits the Title: from now on it keeps exactly [text] (ADR-0005). */
fun Post.editTitle(text: String): Post =
    if (text == title) this else copy(title = text, titleHandEdited = true)

/** Hand-edits the Description, so Sync stops overwriting it; the Title follows unless hand-edited (ADR-0005). */
fun Post.editDescription(text: String): Post =
    if (text == description) {
        this
    } else {
        copy(
            description = text,
            descriptionHandEdited = true,
            title = if (titleHandEdited) title else titleFrom(text),
        )
    }

fun Post.editPostNote(text: String): Post = if (text == postNote) this else copy(postNote = text)

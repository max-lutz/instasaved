package com.maxlutz.instasaved.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.collections.ColorDot
import com.maxlutz.instasaved.collections.ColorPicker
import com.maxlutz.instasaved.data.MAX_TAGS_PER_POST
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Tag
import com.maxlutz.instasaved.data.nextColor
import com.maxlutz.instasaved.data.sameName

/** A Tag as a pill in its color. */
@Composable
fun TagChip(tag: Tag, modifier: Modifier = Modifier) {
    Surface(color = Color(tag.color), contentColor = Color.White, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            tag.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * Creates or edits a Tag: name and palette color. [initial] is a blank Tag with the next palette color for a new one.
 * A name taken by one of [otherNames] can't be saved.
 */
@Composable
fun TagEditorDialog(
    title: String,
    initial: Tag,
    otherNames: List<String>,
    onSave: (Tag) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var color by rememberSaveable { mutableIntStateOf(initial.color) }
    val taken = otherNames.any { sameName(it, name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tag_name)) },
                    singleLine = true,
                    isError = taken,
                    supportingText = if (taken) {
                        { Text(stringResource(R.string.tag_name_taken)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ColorPicker(color, onPick = { color = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(name = name.trim(), color = color)) },
                enabled = name.isNotBlank() && !taken,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Picks a Post's Tags among every Tag: each tap adds or removes one at once. Once the Post has [MAX_TAGS_PER_POST],
 * the others can't be picked. "New Tag…" creates one and puts it on the Post, handed to [onNew].
 *
 * @param tags every Tag, alphabetically.
 * @param onPost the Tags the Post carries.
 * @param message shown above the Tags, e.g. what just happened to the Post.
 */
@Composable
fun TagPickerDialog(
    title: String,
    tags: List<Tag>,
    onPost: List<Tag>,
    onAdd: (Tag) -> Unit,
    onRemove: (Tag) -> Unit,
    onNew: (Tag) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    val onPostIds = onPost.map { it.id }.toSet()
    val full = onPostIds.size >= MAX_TAGS_PER_POST

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                message?.let { Text(it) }
                if (tags.isEmpty()) {
                    Text(
                        stringResource(R.string.no_tags),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        val picked = tag.id in onPostIds
                        FilterChip(
                            selected = picked,
                            onClick = { if (picked) onRemove(tag) else onAdd(tag) },
                            enabled = picked || !full,
                            label = { Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { ColorDot(tag.color) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.max_tags_per_post, MAX_TAGS_PER_POST),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (full) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
        dismissButton = {
            TextButton(onClick = { creating = true }, enabled = !full) {
                Text(stringResource(R.string.new_tag_ellipsis))
            }
        },
    )

    if (creating) {
        TagEditorDialog(
            title = stringResource(R.string.new_tag),
            initial = Tag(name = "", color = nextColor(tags.map { it.color })),
            otherNames = tags.map { it.name },
            onSave = {
                creating = false
                onNew(it)
            },
            onDismiss = { creating = false },
        )
    }
}

@Preview
@Composable
private fun TagPickerDialogPreview() {
    val tags = listOf(Tag(1, "Quick", PALETTE[1]), Tag(2, "Vegan", PALETTE[3]), Tag(3, "Weekend", PALETTE[5]))
    MaterialTheme {
        TagPickerDialog(
            title = "Added to To sort",
            message = "Tag it now?",
            tags = tags,
            onPost = tags.take(1),
            onAdd = {},
            onRemove = {},
            onNew = {},
            onDismiss = {},
        )
    }
}

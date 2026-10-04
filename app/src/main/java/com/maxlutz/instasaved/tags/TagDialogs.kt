package com.maxlutz.instasaved.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.collections.ColorDot
import com.maxlutz.instasaved.collections.ColorPicker
import com.maxlutz.instasaved.data.MAX_TAGS_PER_POST
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.data.Tag
import com.maxlutz.instasaved.data.nextColor
import com.maxlutz.instasaved.data.sameName
import com.maxlutz.instasaved.ui.InstaSavedTheme
import com.maxlutz.instasaved.ui.SearchField

/** A Tag as a pill in its color, ending with a × that calls [onRemove] when there is one. */
@Composable
fun TagChip(tag: Tag, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    // Not a Surface: its clip would cut the ×'s touch target, which reaches past the pill to the full size.
    Row(modifier.background(Color(tag.color), RoundedCornerShape(50)), verticalAlignment = Alignment.CenterVertically) {
        Text(
            tag.name,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 12.dp, end = if (onRemove == null) 12.dp else 4.dp, top = 6.dp, bottom = 6.dp),
        )
        if (onRemove != null) {
            Icon(
                painterResource(R.drawable.ic_close),
                stringResource(R.string.remove_tag, tag.name),
                Modifier.padding(end = 8.dp).clickable(onClick = onRemove).size(16.dp),
                tint = Color.White,
            )
        }
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
 * the others can't be picked. Typing narrows the Tags; the ones used in the Post's Collection come first.
 * "New Tag…" creates one and puts it on the Post, handed to [onNew]; so does a search that matches no Tag.
 *
 * @param tags every Tag, alphabetically.
 * @param onPost the Tags the Post carries.
 * @param inCollection the ids of the Tags used in the Post's Collection, see [tagIdsInCollectionOf].
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
    inCollection: Set<Long> = emptySet(),
    message: String? = null,
) {
    val onPostIds = onPost.map { it.id }.toSet()
    TagPickerDialog(
        title = title,
        tags = tags,
        // One Post, whatever its id.
        tagging = Tagging(listOf(0L), onPostIds.map { PostTag(0L, it) }),
        onTap = { if (it.id in onPostIds) onRemove(it) else onAdd(it) },
        onNew = onNew,
        onDismiss = onDismiss,
        inCollection = inCollection,
        message = message,
    )
}

/**
 * Picks the Tags of the Posts of [tagging] together: a Tag is selected when all of them carry it, half-selected
 * and saying how many when only some do, and a tap, handed to [onTap], puts it on all or takes it off all, see
 * [Tagging.tap].
 * A Tag that fits on none of them can't be picked.
 *
 * @param inCollection the ids of the Tags used in the Collection the Posts are all in, see [tagIdsInCollectionOf].
 */
@Composable
fun TagPickerDialog(
    title: String,
    tags: List<Tag>,
    tagging: Tagging,
    onTap: (Tag) -> Unit,
    onNew: (Tag) -> Unit,
    onDismiss: () -> Unit,
    inCollection: Set<Long> = emptySet(),
    message: String? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // The name the New Tag dialog opens with, while a Tag is being created.
    var creating by rememberSaveable { mutableStateOf<String?>(null) }
    val full = tagging.full
    val choices = tagChoices(tags, query, inCollection)
    val onSome = stringResource(R.string.tag_on_some_posts)

    @Composable
    fun Chips(tags: List<Tag>) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                val carried = tagging.carried(tag.id)
                FilterChip(
                    selected = carried == Carried.All,
                    onClick = { onTap(tag) },
                    enabled = tagging.canTap(tag.id),
                    label = { Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { ColorDot(tag.color) },
                    trailingIcon = if (carried == Carried.Some) {
                        {
                            Text(
                                stringResource(R.string.tag_on_count, tagging.countCarrying(tag.id), tagging.postCount),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    } else {
                        null
                    },
                    modifier = if (carried == Carried.Some) {
                        Modifier.semantics { stateDescription = onSome }
                    } else {
                        Modifier
                    },
                    // Half-selected: the fill of a selected chip, inside the outline of an unselected one.
                    colors = if (carried == Carried.Some) {
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    } else {
                        FilterChipDefaults.filterChipColors()
                    },
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Searching must not hide the buttons behind the keyboard.
        modifier = Modifier.imePadding(),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                message?.let { Text(it) }
                SearchField(query, { query = it }, stringResource(R.string.search_tags), Modifier.fillMaxWidth())
                // Only the Tags scroll: the search and the limit stay in view.
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    when {
                        tags.isEmpty() && query.isBlank() -> Text(
                            stringResource(R.string.no_tags),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        choices.isEmpty -> TextButton(onClick = { creating = query.trim() }, enabled = !full) {
                            Text(stringResource(R.string.create_tag, query.trim()))
                        }
                        else -> {
                            Chips(choices.inCollection)
                            if (choices.inCollection.isNotEmpty() && choices.others.isNotEmpty()) {
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            }
                            Chips(choices.others)
                        }
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
            TextButton(onClick = { creating = "" }, enabled = !full) {
                Text(stringResource(R.string.new_tag_ellipsis))
            }
        },
    )

    creating?.let { name ->
        TagEditorDialog(
            title = stringResource(R.string.new_tag),
            initial = Tag(name = name, color = nextColor(tags.map { it.color })),
            otherNames = tags.map { it.name },
            onSave = {
                creating = null
                onNew(it)
            },
            onDismiss = { creating = null },
        )
    }
}

@Preview
@Composable
private fun TagPickerDialogPreview() {
    val tags = listOf(Tag(1, "Quick", PALETTE[1]), Tag(2, "Vegan", PALETTE[3]), Tag(3, "Weekend", PALETTE[5]))
    InstaSavedTheme {
        TagPickerDialog(
            title = "Added to To sort",
            message = "Tag it now?",
            tags = tags,
            onPost = tags.take(1),
            inCollection = setOf(2),
            onAdd = {},
            onRemove = {},
            onNew = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun TagPickerDialogForSeveralPostsPreview() {
    val tags = listOf(Tag(1, "Quick", PALETTE[1]), Tag(2, "Vegan", PALETTE[3]), Tag(3, "Weekend", PALETTE[5]))
    InstaSavedTheme {
        TagPickerDialog(
            title = "Tags of 2 Posts",
            tags = tags,
            tagging = Tagging(listOf(1, 2), listOf(PostTag(1, 1), PostTag(2, 1), PostTag(1, 2))),
            onTap = {},
            onNew = {},
            onDismiss = {},
        )
    }
}

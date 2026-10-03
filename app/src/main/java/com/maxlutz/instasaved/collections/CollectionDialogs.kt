package com.maxlutz.instasaved.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.sameName
import com.maxlutz.instasaved.ui.InstaSavedTheme

@Composable
fun ColorDot(color: Int, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(modifier.size(size).clip(CircleShape).background(Color(color)))
}

/**
 * Creates or edits a Collection: name, palette color and Collection Note. [initial] is a blank Collection with the
 * next palette color for a new one. A name taken by one of [otherNames] can't be saved.
 */
@Composable
fun CollectionEditorDialog(
    title: String,
    initial: Collection,
    otherNames: List<String>,
    onSave: (Collection) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var color by rememberSaveable { mutableIntStateOf(initial.color) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    val taken = otherNames.any { sameName(it, name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.collection_name)) },
                    singleLine = true,
                    isError = taken,
                    supportingText = if (taken) {
                        { Text(stringResource(R.string.collection_name_taken)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                ColorPicker(color, onPick = { color = it })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.collection_note)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(name = name.trim(), color = color, note = note)) },
                enabled = name.isNotBlank() && !taken,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun ColorPicker(selected: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PALETTE.chunked(6).forEachIndexed { row, colors ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEachIndexed { i, color ->
                    val description = stringResource(R.string.palette_color, row * 6 + i + 1)
                    ColorDot(
                        color,
                        size = 32.dp,
                        modifier = Modifier
                            .then(
                                if (color == selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .selectable(selected = color == selected, role = Role.RadioButton) { onPick(color) }
                            .semantics { contentDescription = description },
                    )
                }
            }
        }
    }
}

/**
 * Asks what to do with a Collection's Posts when deleting it (ADR-0010): keep them (to To sort) or delete them
 * too (to Recently deleted). An empty Collection just asks for confirmation.
 */
@Composable
fun DeleteCollectionDialog(
    name: String,
    postCount: Int,
    onKeepPosts: () -> Unit,
    onDeletePosts: () -> Unit,
    onDismiss: () -> Unit,
) {
    val errorColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_collection_title, name)) },
        text = {
            Text(
                if (postCount == 0) {
                    stringResource(R.string.delete_collection_empty)
                } else {
                    pluralStringResource(R.plurals.delete_collection_posts, postCount, postCount)
                },
            )
        },
        confirmButton = {
            if (postCount == 0) {
                TextButton(onClick = onKeepPosts, colors = errorColors) { Text(stringResource(R.string.delete)) }
            } else {
                // Long labels: stacked, as Material asks for.
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = onKeepPosts) { Text(stringResource(R.string.keep_posts)) }
                    TextButton(onClick = onDeletePosts, colors = errorColors) {
                        Text(stringResource(R.string.delete_posts_too))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        },
        dismissButton = if (postCount == 0) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        } else {
            null
        },
    )
}

@Preview
@Composable
private fun CollectionEditorDialogPreview() {
    InstaSavedTheme {
        CollectionEditorDialog(
            title = "New Collection",
            initial = Collection(name = "Recipes", color = PALETTE[0]),
            otherNames = listOf("recipes"),
            onSave = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun DeleteCollectionDialogPreview() {
    InstaSavedTheme { DeleteCollectionDialog("Recipes", 3, onKeepPosts = {}, onDeletePosts = {}, onDismiss = {}) }
}

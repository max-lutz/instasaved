package com.maxlutz.instasaved.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.collections.CollectionEditorDialog
import com.maxlutz.instasaved.collections.ColorDot
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.editDescription
import com.maxlutz.instasaved.data.editPostNote
import com.maxlutz.instasaved.data.editTitle
import com.maxlutz.instasaved.data.nextColor

/**
 * An opened Post: its Embed, then its Collection, Title, Description and Post Note, editable in place.
 * Every text edit is handed to [onTextChange] as the whole edited Post, flags included.
 *
 * @param collections every Collection, alphabetically, to pick the Post's from.
 * @param onCollectionChange the picked Collection's id, or null for To sort.
 * @param onNewCollection a Collection created from the picker, to create and put the Post in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    post: Post,
    collections: List<Collection>,
    onTextChange: (Post) -> Unit,
    onCollectionChange: (Long?) -> Unit,
    onNewCollection: (Collection) -> Unit,
    onOpenInInstagram: () -> Unit,
    onDelete: () -> Unit,
    showEmbed: Boolean = true,
) {
    // Edits live here and are only written out: the stored Post lags behind while saves are in flight.
    var draft by remember(post.id) { mutableStateOf(post) }
    fun edit(change: Post.() -> Post) {
        val edited = draft.change()
        if (edited != draft) {
            draft = edited
            onTextChange(edited)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        draft.title.ifBlank { stringResource(R.string.post_detail_untitled) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Drags on the Embed scroll only the Embed, so it never fills the screen: the rest scrolls the page.
            val embedHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
            if (showEmbed) Embed(post.shortcode, Modifier.fillMaxWidth().height(embedHeight))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CollectionPicker(
                    current = collections.find { it.id == post.collectionId },
                    collections = collections,
                    onPick = onCollectionChange,
                    onNew = onNewCollection,
                )
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { edit { editTitle(it) } },
                    label = { Text(stringResource(R.string.title)) },
                    supportingText = if (draft.titleHandEdited) null else {
                        { Text(stringResource(R.string.title_follows_description)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { edit { editDescription(it) } },
                    label = { Text(stringResource(R.string.description)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.postNote,
                    onValueChange = { edit { editPostNote(it) } },
                    label = { Text(stringResource(R.string.post_note)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenInInstagram) { Text(stringResource(R.string.open_in_instagram)) }
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}

/** The Post's Collection (or To sort) as a button opening a menu of every Collection, plus "New Collection…". */
@Composable
private fun CollectionPicker(
    current: Collection?,
    collections: List<Collection>,
    onPick: (Long?) -> Unit,
    onNew: (Collection) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (current != null) ColorDot(current.color)
                Text(
                    current?.name ?: stringResource(R.string.to_sort),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.to_sort)) },
                onClick = {
                    expanded = false
                    onPick(null)
                },
            )
            collections.forEach { collection ->
                DropdownMenuItem(
                    text = { Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { ColorDot(collection.color) },
                    onClick = {
                        expanded = false
                        onPick(collection.id)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.new_collection_ellipsis)) },
                onClick = {
                    expanded = false
                    creating = true
                },
            )
        }
    }

    if (creating) {
        CollectionEditorDialog(
            title = stringResource(R.string.new_collection),
            initial = Collection(name = "", color = nextColor(collections.map { it.color })),
            otherNames = collections.map { it.name },
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
private fun PostDetailScreenPreview() {
    MaterialTheme {
        PostDetailScreen(
            Post(
                shortcode = "C1a2B3c4D5e",
                url = "https://www.instagram.com/p/C1a2B3c4D5e/",
                addedAt = 0,
                title = "Best pasta in town",
                description = "Best pasta in town. Recipe below!",
                collectionId = 1,
            ),
            collections = listOf(Collection(1, "🍝 Pasta", PALETTE[0])),
            onTextChange = {},
            onCollectionChange = {},
            onNewCollection = {},
            onOpenInInstagram = {},
            onDelete = {},
            showEmbed = false,
        )
    }
}

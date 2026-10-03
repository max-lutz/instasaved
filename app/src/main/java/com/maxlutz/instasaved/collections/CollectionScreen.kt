package com.maxlutz.instasaved.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.grid.PostGridScreen

/** A Collection's Posts, its Collection Note above them, and editing or deleting the Collection. */
@Composable
fun CollectionScreen(
    collection: Collection,
    posts: List<Post>,
    otherNames: List<String>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (Post) -> Unit,
    onSave: (Collection) -> Unit,
    onDeleteKeepingPosts: () -> Unit,
    onDeleteWithPosts: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    PostGridScreen(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorDot(collection.color, size = 16.dp)
                Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        posts = posts,
        emptyText = stringResource(R.string.collection_empty),
        snackbar = snackbar,
        onBack = onBack,
        onOpen = onOpen,
        actions = {
            TextButton(onClick = { editing = true }) { Text(stringResource(R.string.edit)) }
            TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.delete)) }
        },
        header = collection.note.takeIf { it.isNotBlank() }?.let { note ->
            {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
    )

    if (editing) {
        CollectionEditorDialog(
            title = stringResource(R.string.edit_collection),
            initial = collection,
            otherNames = otherNames,
            onSave = {
                editing = false
                onSave(it)
            },
            onDismiss = { editing = false },
        )
    }
    if (deleting) {
        DeleteCollectionDialog(
            name = collection.name,
            postCount = posts.size,
            onKeepPosts = {
                deleting = false
                onDeleteKeepingPosts()
            },
            onDeletePosts = {
                deleting = false
                onDeleteWithPosts()
            },
            onDismiss = { deleting = false },
        )
    }
}

@Preview
@Composable
private fun CollectionScreenPreview() {
    MaterialTheme {
        CollectionScreen(
            collection = Collection(1, "🍝 Pasta", PALETTE[0], "Weeknight dinners, nothing over 30 minutes."),
            posts = listOf(Post(id = 1, shortcode = "C1a2B3c4D5e", url = "", addedAt = 0, title = "Carbonara")),
            otherNames = emptyList(),
            snackbar = remember { SnackbarHostState() },
            onBack = {},
            onOpen = {},
            onSave = {},
            onDeleteKeepingPosts = {},
            onDeleteWithPosts = {},
        )
    }
}

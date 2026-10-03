package com.maxlutz.instasaved.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.data.Tag
import com.maxlutz.instasaved.grid.PostGridScreen
import com.maxlutz.instasaved.grid.TagGrouping
import com.maxlutz.instasaved.ui.InstaSavedTheme
import com.maxlutz.instasaved.ui.Pill
import com.maxlutz.instasaved.ui.SoftButton
import java.io.File

/**
 * A Collection's Posts, its Collection Note above them, and editing or deleting the Collection. Once any of its Posts
 * carries a Tag, a chip groups them by Tag: under each Tag the Posts carrying it, then those with none.
 *
 * @param tags every Tag, alphabetically.
 * @param postTags which Posts carry which Tags.
 */
@Composable
fun CollectionScreen(
    collection: Collection,
    posts: List<Post>,
    thumbnailOf: (Post) -> File?,
    tags: List<Tag>,
    postTags: List<PostTag>,
    groupByTag: Boolean,
    onGroupByTagChange: (Boolean) -> Unit,
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
    val anyTagged = remember(posts, postTags) {
        val ids = posts.mapTo(HashSet()) { it.id }
        postTags.any { it.postId in ids }
    }

    PostGridScreen(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorDot(collection.color, size = 16.dp)
                Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        posts = posts,
        thumbnailOf = thumbnailOf,
        emptyText = stringResource(R.string.collection_empty),
        snackbar = snackbar,
        onBack = onBack,
        onOpen = onOpen,
        actions = {
            SoftButton(stringResource(R.string.edit), onClick = { editing = true })
            SoftButton(stringResource(R.string.delete), onClick = { deleting = true }, danger = true)
        },
        header = if (collection.note.isBlank() && !anyTagged) {
            null
        } else {
            {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (collection.note.isNotBlank()) Text(collection.note, style = MaterialTheme.typography.bodyMedium)
                    if (anyTagged) {
                        Pill(onClick = { onGroupByTagChange(!groupByTag) }, selected = groupByTag) {
                            Text(stringResource(R.string.group_by_tag), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        },
        grouping = if (groupByTag && anyTagged) TagGrouping(tags, postTags) else null,
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
    InstaSavedTheme {
        CollectionScreen(
            collection = Collection(1, "🍝 Pasta", PALETTE[0], "Weeknight dinners, nothing over 30 minutes."),
            posts = listOf(
                Post(id = 1, shortcode = "C1a2B3c4D5e", url = "", addedAt = 0, title = "Carbonara"),
                Post(id = 2, shortcode = "B9x_Y-z0", url = "", addedAt = 0, title = "Dal"),
            ),
            thumbnailOf = { null },
            tags = listOf(Tag(1, "Vegan", PALETTE[3])),
            postTags = listOf(PostTag(2, 1)),
            groupByTag = true,
            onGroupByTagChange = {},
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

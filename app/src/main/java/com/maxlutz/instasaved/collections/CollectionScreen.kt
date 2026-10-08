package com.maxlutz.instasaved.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
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
import com.maxlutz.instasaved.grid.BarePill
import com.maxlutz.instasaved.grid.BulkActions
import com.maxlutz.instasaved.grid.PostGridScreen
import com.maxlutz.instasaved.grid.TagGrouping
import com.maxlutz.instasaved.grid.rememberBareFilter
import com.maxlutz.instasaved.ui.InstaSavedTheme
import com.maxlutz.instasaved.ui.Pill
import com.maxlutz.instasaved.ui.SoftButton
import java.io.File

/**
 * A Collection's Posts, its Collection Note above them, and editing or deleting the Collection, or moving all its
 * Posts to another. Once any of its Posts carries a Tag, a chip groups them by Tag: under each Tag the Posts
 * carrying it, then those with none. Next to it, with "Show Bare Posts" on and a Bare Post among them, a chip shows
 * only the Bare Posts, as one grid.
 *
 * @param others the other Collections, alphabetically.
 * @param onMoveAll the Collection to move all the Posts to, and whether to delete this one along the way.
 * @param onNoPostsToMove "Move all Posts to…" was picked with no Post to move.
 * @param tags every Tag, alphabetically.
 * @param postTags which Posts carry which Tags.
 * @param showBare whether "Show Bare Posts" is on.
 * @param bareOnly whether the user asked for only the Bare Posts.
 * @param sections what the editor needs to put the Collection in a Section.
 * @param bulk what selecting several Posts needs to act on them together.
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
    showBare: Boolean,
    bareOnly: Boolean,
    onBareOnlyChange: (Boolean) -> Unit,
    others: List<Collection>,
    sections: SectionChoice,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (Post) -> Unit,
    onSave: (Collection) -> Unit,
    onDeleteKeepingPosts: () -> Unit,
    onDeleteWithPosts: () -> Unit,
    onMoveAll: (Collection, Boolean) -> Unit,
    onNoPostsToMove: () -> Unit,
    bulk: BulkActions,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pickingTarget by remember { mutableStateOf(false) }
    var targetId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Null once the target is deleted, which closes the dialog.
    val target = others.find { it.id == targetId }
    val anyTagged = remember(posts, postTags) {
        val ids = posts.mapTo(HashSet()) { it.id }
        postTags.any { it.postId in ids }
    }
    val bare = rememberBareFilter(posts, postTags, offered = showBare, on = bareOnly, onChange = onBareOnlyChange)

    PostGridScreen(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorDot(collection.color, size = 16.dp)
                Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        posts = bare.shown,
        thumbnailOf = thumbnailOf,
        emptyText = stringResource(R.string.collection_empty),
        snackbar = snackbar,
        onBack = onBack,
        onOpen = onOpen,
        actions = {
            SoftButton(stringResource(R.string.edit), onClick = { editing = true })
            SoftButton(stringResource(R.string.delete), onClick = { deleting = true }, danger = true)
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_more_horiz), stringResource(R.string.more_actions))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_all_posts_to)) },
                        onClick = {
                            menuOpen = false
                            if (posts.isEmpty()) onNoPostsToMove() else pickingTarget = true
                        },
                        enabled = others.isNotEmpty(),
                    )
                }
                // To sort is not a target: deleting the Collection and keeping its Posts does that (ADR-0010).
                CollectionMenu(
                    expanded = pickingTarget,
                    onDismiss = { pickingTarget = false },
                    collections = others,
                    onPick = { targetId = it },
                    onNew = null,
                    offerToSort = false,
                )
            }
        },
        header = if (collection.note.isBlank() && !anyTagged && bare.count == 0) {
            null
        } else {
            {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (collection.note.isNotBlank()) Text(collection.note, style = MaterialTheme.typography.bodyMedium)
                    if (anyTagged || bare.count > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (anyTagged) {
                                Pill(onClick = { onGroupByTagChange(!groupByTag) }, selected = groupByTag) {
                                    Text(
                                        stringResource(R.string.group_by_tag),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                            if (bare.count > 0) BarePill(bare, onBareOnlyChange)
                        }
                    }
                }
            }
        },
        // Every Bare Post would be under "No Tag".
        grouping = if (groupByTag && anyTagged && !bare.active) TagGrouping(tags, postTags) else null,
        bulk = bulk,
    )

    if (editing) {
        CollectionEditorDialog(
            title = stringResource(R.string.edit_collection),
            initial = collection,
            otherNames = others.map { it.name },
            sections = sections,
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
    if (target != null) {
        MoveAllPostsDialog(
            from = collection.name,
            to = target.name,
            postCount = posts.size,
            onMove = {
                targetId = null
                onMoveAll(target, false)
            },
            onMoveAndDelete = {
                targetId = null
                onMoveAll(target, true)
            },
            onDismiss = { targetId = null },
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
            showBare = true,
            bareOnly = false,
            onBareOnlyChange = {},
            others = emptyList(),
            sections = SectionChoice(),
            snackbar = remember { SnackbarHostState() },
            onBack = {},
            onOpen = {},
            onSave = {},
            onDeleteKeepingPosts = {},
            onDeleteWithPosts = {},
            onMoveAll = { _, _ -> },
            onNoPostsToMove = {},
            bulk = BulkActions(),
        )
    }
}

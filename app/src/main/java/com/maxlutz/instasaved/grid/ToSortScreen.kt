package com.maxlutz.instasaved.grid

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.collections.CollectionEditorDialog
import com.maxlutz.instasaved.collections.CollectionMenu
import com.maxlutz.instasaved.collections.SectionChoice
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.nextColor
import com.maxlutz.instasaved.ui.InstaSavedTheme
import com.maxlutz.instasaved.ui.ScreenTitle
import com.maxlutz.instasaved.ui.SoftButton
import java.io.File

/**
 * To sort as an inbox: the Posts with no Collection, newest first, each with a File button to put it in one.
 * A long-press on a Post selects several, to act on them together.
 *
 * @param collections every Collection, alphabetically, to file a Post in.
 * @param now the current time in epoch milliseconds, to say how long ago each Post was added.
 * @param onNewCollection a Collection created from a Post's File menu, to create and put that Post in.
 * @param sections what creating that Collection needs to put it in a Section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToSortScreen(
    posts: List<Post>,
    thumbnailOf: (Post) -> File?,
    collections: List<Collection>,
    snackbar: SnackbarHostState,
    now: Long,
    onOpen: (Post) -> Unit,
    onFile: (Post, Long) -> Unit,
    onNewCollection: (Post, Collection) -> Unit,
    sections: SectionChoice,
    bulk: BulkActions,
    bottomBar: @Composable () -> Unit,
) {
    var creatingForId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selection = rememberSelection(posts)

    Scaffold(
        topBar = {
            if (selection.active) {
                SelectionTopBar(selection, bulk)
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.to_sort), style = ScreenTitle) },
                    actions = {
                        Text(
                            pluralStringResource(R.plurals.post_count, posts.size, posts.size),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 14.dp),
                        )
                    },
                )
            }
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (posts.isEmpty()) {
            Box(Modifier.padding(padding)) { EmptyText(stringResource(R.string.to_sort_empty)) }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(posts, key = { it.id }) { post ->
                    HorizontalDivider()
                    ToSortRow(
                        post = post,
                        thumbnail = thumbnailOf(post),
                        collections = collections,
                        now = now,
                        selecting = selection.active,
                        selected = post in selection,
                        onClick = { if (selection.active) selection.toggle(post) else onOpen(post) },
                        onLongClick = { selection.toggle(post) },
                        onFile = { onFile(post, it) },
                        onNew = { creatingForId = post.id },
                    )
                }
            }
        }
    }

    posts.find { it.id == creatingForId }?.let { post ->
        CollectionEditorDialog(
            title = stringResource(R.string.new_collection),
            initial = Collection(name = "", color = nextColor(collections.map { it.color })),
            otherNames = collections.map { it.name },
            sections = sections,
            onSave = {
                creatingForId = null
                onNewCollection(post, it)
            },
            onDismiss = { creatingForId = null },
        )
    }
}

/**
 * A Post waiting in To sort: its Thumbnail, its Title, who posted it and when it was added, and the File button,
 * which leaves while the screen is [selecting].
 */
@Composable
private fun ToSortRow(
    post: Post,
    thumbnail: File?,
    collections: List<Collection>,
    now: Long,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFile: (Long) -> Unit,
    onNew: () -> Unit,
) {
    var filing by remember { mutableStateOf(false) }
    val owner = post.ownerName.ifBlank { post.ownerUsername }
    val added = DateUtils.getRelativeTimeSpanString(post.addedAt, now, DateUtils.DAY_IN_MILLIS).toString()

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PostCard(
            post,
            thumbnail,
            onClick = onClick,
            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
            onLongClick = onLongClick,
            selected = selected,
        )
        Column(Modifier.weight(1f)) {
            Text(
                post.title.ifBlank { post.shortcode },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (owner.isBlank()) added else stringResource(R.string.owner_and_added, owner, added),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!selecting) Box {
            SoftButton(stringResource(R.string.file) + " ▾", onClick = { filing = true })
            CollectionMenu(
                expanded = filing,
                onDismiss = { filing = false },
                collections = collections,
                onPick = { id -> id?.let(onFile) },
                onNew = onNew,
                offerToSort = false,
            )
        }
    }
}

@Preview
@Composable
private fun ToSortScreenPreview() {
    InstaSavedTheme {
        ToSortScreen(
            listOf("Rice cooker tricks" to "Tiny Kitchen", "Shibuya at night" to "", "" to "").mapIndexed { i, post ->
                val (title, owner) = post
                Post(id = i + 1L, shortcode = "C1a2B3c4D5e$i", url = "", addedAt = 0, title = title, ownerName = owner)
            },
            thumbnailOf = { null },
            collections = listOf(Collection(1, "🍝 Pasta", PALETTE[0])),
            snackbar = remember { SnackbarHostState() },
            now = 3 * DateUtils.DAY_IN_MILLIS,
            onOpen = {},
            onFile = { _, _ -> },
            onNewCollection = { _, _ -> },
            sections = SectionChoice(),
            bulk = BulkActions(),
            bottomBar = {},
        )
    }
}

@Preview
@Composable
private fun EmptyToSortScreenPreview() {
    InstaSavedTheme {
        ToSortScreen(
            emptyList(),
            thumbnailOf = { null },
            collections = emptyList(),
            snackbar = remember { SnackbarHostState() },
            now = 0,
            onOpen = {},
            onFile = { _, _ -> },
            onNewCollection = { _, _ -> },
            sections = SectionChoice(),
            bulk = BulkActions(),
            bottomBar = {},
        )
    }
}

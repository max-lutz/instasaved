package com.maxlutz.instasaved.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.collections.ColorDot
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.data.Tag
import com.maxlutz.instasaved.ui.InstaSavedTheme
import java.io.File

/**
 * A view of Posts (All posts, a Collection, Recently deleted) as a 3-column grid, in the order given, with an
 * optional [header] above it.
 *
 * @param thumbnailOf the Post's Thumbnail file, or null while it has none.
 * @param overlayOf a few words to write across the bottom of a Post's card, if any.
 * @param grouping the Tags to group the Posts by; null shows them as one grid.
 * @param bulk what a long-press on a Post needs to select several and act on them together; null leaves it out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostGridScreen(
    title: @Composable () -> Unit,
    posts: List<Post>,
    thumbnailOf: (Post) -> File?,
    emptyText: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (Post) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    overlayOf: (@Composable (Post) -> String)? = null,
    grouping: TagGrouping? = null,
    bulk: BulkActions? = null,
) {
    val selection = rememberSelection(posts)
    val groups = remember(posts, grouping) {
        grouping?.let { Browse(groupByTag = true).arrange(posts, it.tags, it.postTags) } ?: listOf(PostGroup(null, posts))
    }
    Scaffold(
        topBar = {
            if (bulk != null && selection.active) {
                SelectionTopBar(selection, bulk)
            } else {
                TopAppBar(
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                        }
                    },
                    actions = actions,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (posts.isEmpty()) {
                header?.invoke()
                EmptyText(emptyText)
            } else {
                PostGrid(
                    groups = groups,
                    grouped = grouping != null,
                    thumbnailOf = thumbnailOf,
                    onOpen = onOpen,
                    header = header,
                    overlayOf = overlayOf,
                    selection = selection.takeIf { bulk != null },
                )
            }
        }
    }
}

/** The Tags (alphabetically) to group a view's Posts by, with the Posts they are on. */
data class TagGrouping(val tags: List<Tag>, val postTags: List<PostTag>)

/** What a view says in place of its Posts when it has none to show. */
@Composable
internal fun EmptyText(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The 3-column grid of [groups], each under the name of its Tag when [grouped].
 *
 * @param selection picks Posts on a long-press, then on a tap while it is active; null only opens them.
 */
@Composable
internal fun PostGrid(
    groups: List<PostGroup>,
    grouped: Boolean,
    thumbnailOf: (Post) -> File?,
    onOpen: (Post) -> Unit,
    header: (@Composable () -> Unit)? = null,
    overlayOf: (@Composable (Post) -> String)? = null,
    selection: Selection? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
        groups.forEach { group ->
            if (grouped) {
                item(key = "tag-${group.tag?.id}", span = { GridItemSpan(maxLineSpan) }) { GroupHeader(group) }
            }
            // A Post is shown once per Tag it carries.
            items(group.posts, key = { "${group.tag?.id}-${it.id}" }) { post ->
                PostCard(
                    post,
                    thumbnailOf(post),
                    overlay = overlayOf?.invoke(post),
                    onClick = { if (selection?.active == true) selection.toggle(post) else onOpen(post) },
                    onLongClick = selection?.let { { it.toggle(post) } },
                    selected = selection != null && post in selection,
                )
            }
        }
    }
}

/** The Tag a group's Posts carry, or "No Tag", with how many Posts are shown under it. */
@Composable
private fun GroupHeader(group: PostGroup) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        group.tag?.let { ColorDot(it.color) }
        Text(
            group.tag?.name ?: stringResource(R.string.no_tag),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            group.posts.size.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The Post's Thumbnail as a square, or a placeholder card naming its owner until there is one. A reel is marked in
 * the top corner, a Post with a Post Note in the bottom one; [overlay] is written across the bottom instead.
 * A [selected] Post is tinted and checked.
 */
@Composable
internal fun PostCard(
    post: Post,
    thumbnail: File?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: String? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    // The owner is only known once Sync has seen the Post.
    val label = post.ownerName.ifBlank { post.ownerUsername }.ifBlank { post.title }.ifBlank { post.shortcode }
    Box(
        modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (thumbnail != null) {
            AsyncImage(thumbnail, contentDescription = label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(6.dp),
            )
        }
        if (thumbnail != null && "/reel" in post.url) {
            Icon(
                painterResource(R.drawable.ic_play),
                contentDescription = null,
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp),
                tint = Color.White,
            )
        }
        if (overlay == null && post.postNote.isNotBlank()) {
            Icon(
                painterResource(R.drawable.ic_note),
                contentDescription = stringResource(R.string.has_post_note),
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(18.dp)
                    .background(Color.Black.copy(alpha = 0.62f), CircleShape)
                    .padding(3.dp),
                tint = Color.White,
            )
        }
        if (overlay != null) {
            Text(
                overlay,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        if (selected) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
            Icon(
                painterResource(R.drawable.ic_check_circle),
                contentDescription = stringResource(R.string.selected),
                Modifier.align(Alignment.TopStart).padding(4.dp).size(20.dp).background(Color.White, CircleShape),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Every Post, whatever its Collection, newest first. */
@Composable
fun AllScreen(
    posts: List<Post>,
    thumbnailOf: (Post) -> File?,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (Post) -> Unit,
    bulk: BulkActions,
) {
    PostGridScreen(
        title = { Text(stringResource(R.string.all_posts)) },
        posts = posts,
        thumbnailOf = thumbnailOf,
        emptyText = stringResource(R.string.all_empty),
        snackbar = snackbar,
        onBack = onBack,
        onOpen = onOpen,
        bulk = bulk,
    )
}

@Preview
@Composable
private fun AllScreenPreview() {
    InstaSavedTheme {
        AllScreen(
            listOf("Carbonara" to "Pasta Grannies", "Dal" to "", "Ramen" to "Tiny Kitchen").mapIndexed { i, post ->
                val (title, owner) = post
                val note = if (i == 0) "Try this next month." else ""
                Post(id = i + 1L, shortcode = "P$i", url = "", addedAt = 0, title = title, ownerName = owner, postNote = note)
            },
            thumbnailOf = { null },
            snackbar = remember { SnackbarHostState() },
            onBack = {},
            onOpen = {},
            bulk = BulkActions(),
        )
    }
}

@Preview
@Composable
private fun EmptyAllScreenPreview() {
    InstaSavedTheme {
        AllScreen(
            emptyList(),
            thumbnailOf = { null },
            remember { SnackbarHostState() },
            onBack = {},
            onOpen = {},
            bulk = BulkActions(),
        )
    }
}

@Preview
@Composable
private fun SelectedPostCardPreview() {
    InstaSavedTheme {
        PostCard(
            Post(id = 1, shortcode = "P1", url = "", addedAt = 0, title = "Carbonara"),
            thumbnail = null,
            onClick = {},
            modifier = Modifier.size(120.dp),
            selected = true,
        )
    }
}

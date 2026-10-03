package com.maxlutz.instasaved.grid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.collections.ColorDot
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.data.Tag
import java.io.File

/**
 * What a view needs to be searched, sorted and grouped by Tag: how the user is looking at it now, and the Tags
 * (alphabetically) with the Posts they are on.
 */
class Browsing(
    val browse: Browse = Browse(),
    val tags: List<Tag> = emptyList(),
    val postTags: List<PostTag> = emptyList(),
    val onChange: (Browse) -> Unit = {},
)

/**
 * A view of Posts (All, To sort, a Collection) as a 3-column grid, with an optional [header] above it.
 *
 * @param thumbnailOf the Post's Thumbnail file, or null while it has none.
 * @param browsing lets the user search, sort and group the view; null shows [posts] as given.
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
    browsing: Browsing? = null,
) {
    val groups = if (browsing == null) {
        listOf(PostGroup(null, posts))
    } else {
        remember(posts, browsing.browse, browsing.tags, browsing.postTags) {
            browsing.browse.arrange(posts, browsing.tags, browsing.postTags)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
                actions = actions,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (posts.isEmpty()) {
                header?.invoke()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(emptyText, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
            } else {
                if (browsing != null) BrowseBar(browsing.browse, browsing.onChange)
                if (groups.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.search_no_match),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
                        groups.forEach { group ->
                            if (browsing?.browse?.groupByTag == true) {
                                item(key = "tag-${group.tag?.id}", span = { GridItemSpan(maxLineSpan) }) {
                                    GroupHeader(group)
                                }
                            }
                            // A Post is shown once per Tag it carries.
                            items(group.posts, key = { "${group.tag?.id}-${it.id}" }) {
                                PostCard(it, thumbnailOf(it), onClick = { onOpen(it) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The search field, and a menu to pick the sort order and whether to group by Tag. */
@Composable
private fun BrowseBar(browse: Browse, onChange: (Browse) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = browse.query,
            onValueChange = { onChange(browse.copy(query = it)) },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.search)) },
            trailingIcon = if (browse.query.isEmpty()) {
                null
            } else {
                { TextButton(onClick = { onChange(browse.copy(query = "")) }) { Text(stringResource(R.string.clear)) } }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Box {
            TextButton(onClick = { menuOpen = true }) { Text(stringResource(R.string.sort)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                PostSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sort.label)) },
                        leadingIcon = { RadioButton(selected = sort == browse.sort, onClick = null) },
                        onClick = {
                            menuOpen = false
                            onChange(browse.copy(sort = sort))
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.group_by_tag)) },
                    leadingIcon = { Checkbox(checked = browse.groupByTag, onCheckedChange = null) },
                    onClick = {
                        menuOpen = false
                        onChange(browse.copy(groupByTag = !browse.groupByTag))
                    },
                )
            }
        }
    }
}

private val PostSort.label: Int
    get() = when (this) {
        PostSort.Saved -> R.string.sort_saved
        PostSort.Modified -> R.string.sort_modified
        PostSort.Title -> R.string.title
    }

/** The Tag a group's Posts carry, or "No Tag", with how many Posts are shown under it. */
@Composable
private fun GroupHeader(group: PostGroup) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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

/** The Post's Thumbnail, or a placeholder card naming its owner until there is one. */
@Composable
private fun PostCard(post: Post, thumbnail: File?, onClick: () -> Unit) {
    // The owner is only known once Sync has seen the Post.
    val label = post.ownerName.ifBlank { post.ownerUsername }.ifBlank { post.title }.ifBlank { post.shortcode }
    Surface(Modifier.aspectRatio(1f).clickable(onClick = onClick), color = MaterialTheme.colorScheme.surfaceVariant) {
        if (thumbnail != null) {
            AsyncImage(thumbnail, contentDescription = label, contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Every Post, whatever its Collection. */
@Composable
fun AllScreen(
    posts: List<Post>,
    thumbnailOf: (Post) -> File?,
    browsing: Browsing,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (Post) -> Unit,
) {
    PostGridScreen(
        title = { Text(stringResource(R.string.all)) },
        posts = posts,
        thumbnailOf = thumbnailOf,
        emptyText = stringResource(R.string.all_empty),
        snackbar = snackbar,
        onBack = onBack,
        onOpen = onOpen,
        browsing = browsing,
    )
}

@Composable
fun ToSortScreen(
    posts: List<Post>,
    thumbnailOf: (Post) -> File?,
    browsing: Browsing,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (Post) -> Unit,
) {
    PostGridScreen(
        title = { Text(stringResource(R.string.to_sort)) },
        posts = posts,
        thumbnailOf = thumbnailOf,
        emptyText = stringResource(R.string.to_sort_empty),
        snackbar = snackbar,
        onBack = onBack,
        onOpen = onOpen,
        browsing = browsing,
    )
}

@Preview
@Composable
private fun AllScreenGroupedByTagPreview() {
    val vegan = Tag(1, "Vegan", PALETTE[3])
    MaterialTheme {
        AllScreen(
            listOf("Carbonara", "Dal", "Ramen", "Tiramisu").mapIndexed { i, title ->
                Post(id = i + 1L, shortcode = "P$i", url = "", addedAt = 0, title = title)
            },
            thumbnailOf = { null },
            browsing = Browsing(Browse(groupByTag = true), listOf(vegan), listOf(PostTag(2, 1), PostTag(3, 1))),
            snackbar = remember { SnackbarHostState() },
            onBack = {},
            onOpen = {},
        )
    }
}

@Preview
@Composable
private fun ToSortScreenPreview() {
    MaterialTheme {
        ToSortScreen(
            listOf("C1a2B3c4D5e" to "Pasta Grannies", "B9x_Y-z0" to "", "AbCdEf" to "").mapIndexed { i, post ->
                val (shortcode, owner) = post
                val url = "https://www.instagram.com/p/$shortcode/"
                Post(id = i + 1L, shortcode = shortcode, url = url, addedAt = 0, ownerName = owner)
            },
            thumbnailOf = { null },
            browsing = Browsing(),
            remember { SnackbarHostState() },
            onBack = {},
            onOpen = {},
        )
    }
}

@Preview
@Composable
private fun EmptyToSortScreenPreview() {
    MaterialTheme {
        ToSortScreen(
            emptyList(),
            thumbnailOf = { null },
            browsing = Browsing(),
            remember { SnackbarHostState() },
            onBack = {},
            onOpen = {},
        )
    }
}

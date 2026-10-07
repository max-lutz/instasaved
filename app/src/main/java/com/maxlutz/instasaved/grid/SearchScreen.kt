package com.maxlutz.instasaved.grid

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.collections.ColorDot
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.data.Tag
import com.maxlutz.instasaved.ui.InstaSavedTheme
import com.maxlutz.instasaved.ui.Pill
import com.maxlutz.instasaved.ui.SearchField
import java.io.File

/**
 * What the Search screen needs: how the user is looking at the Posts now, and the Tags (alphabetically) with the
 * Posts they are on.
 */
class Browsing(
    val browse: Browse = Browse(),
    val tags: List<Tag> = emptyList(),
    val postTags: List<PostTag> = emptyList(),
    val onChange: (Browse) -> Unit = {},
)

/**
 * Every Post, to search, sort, filter by Tag and group by Tag: the search box, then a row of chips (the sort order
 * and one per Tag), then the grid. A long-press on a Post selects among the Posts found, to act on them together.
 */
@Composable
fun SearchScreen(
    posts: List<Post>,
    thumbnailOf: (Post) -> File?,
    browsing: Browsing,
    snackbar: SnackbarHostState,
    onOpen: (Post) -> Unit,
    bulk: BulkActions,
    bottomBar: @Composable () -> Unit,
) {
    val browse = browsing.browse
    val groups = remember(posts, browse, browsing.tags, browsing.postTags) {
        browse.arrange(posts, browsing.tags, browsing.postTags)
    }
    val selection = rememberSelection(remember(groups) { groups.flatMap { it.posts } })
    Scaffold(
        topBar = {
            if (selection.active) {
                SelectionTopBar(selection, bulk)
            } else Column(Modifier.statusBarsPadding()) {
                SearchField(
                    query = browse.query,
                    onQueryChange = { browsing.onChange(browse.copy(query = it)) },
                    placeholder = stringResource(R.string.search_hint),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SortPill(browse, browsing.onChange)
                    browsing.tags.forEach { tag ->
                        val picked = tag.id == browse.tagId
                        Pill(
                            onClick = { browsing.onChange(browse.copy(tagId = if (picked) null else tag.id)) },
                            selected = picked,
                        ) {
                            ColorDot(tag.color, size = 10.dp)
                            Text(tag.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    }
                }
            }
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(top = 4.dp)) {
            when {
                posts.isEmpty() -> EmptyText(stringResource(R.string.all_empty))
                groups.isEmpty() -> EmptyText(stringResource(R.string.search_no_match))
                else -> PostGrid(
                    groups,
                    grouped = browse.groupByTag,
                    thumbnailOf = thumbnailOf,
                    onOpen = onOpen,
                    selection = selection,
                )
            }
        }
    }
}

/** The chip naming the sort order, opening a menu to pick it and whether to group by Tag. */
@Composable
private fun SortPill(browse: Browse, onChange: (Browse) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Pill(onClick = { menuOpen = true }, selected = true) {
            Text(
                stringResource(R.string.sort_by, stringResource(browse.sort.label)) + " ▾",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

private val PostSort.label: Int
    get() = when (this) {
        PostSort.Saved -> R.string.sort_saved
        PostSort.Modified -> R.string.sort_modified
        PostSort.Title -> R.string.title
    }

@Preview
@Composable
private fun SearchScreenGroupedByTagPreview() {
    val quick = Tag(2, "Quick", PALETTE[5])
    val vegan = Tag(1, "Vegan", PALETTE[3])
    InstaSavedTheme {
        SearchScreen(
            listOf("Carbonara", "Dal", "Ramen", "Tiramisu").mapIndexed { i, title ->
                Post(id = i + 1L, shortcode = "P$i", url = "", addedAt = 0, title = title)
            },
            thumbnailOf = { null },
            browsing = Browsing(
                Browse(groupByTag = true),
                listOf(quick, vegan),
                listOf(PostTag(2, 1), PostTag(3, 1), PostTag(3, 2)),
            ),
            snackbar = remember { SnackbarHostState() },
            onOpen = {},
            bulk = BulkActions(),
            bottomBar = {},
        )
    }
}

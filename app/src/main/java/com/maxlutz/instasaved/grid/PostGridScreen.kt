package com.maxlutz.instasaved.grid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.data.Post

/** A view of Posts (To sort, a Collection) as a 3-column grid, with an optional [header] above it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostGridScreen(
    title: @Composable () -> Unit,
    posts: List<Post>,
    emptyText: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (Post) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    header: (@Composable () -> Unit)? = null,
) {
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
                    items(posts, key = { it.id }) { PlaceholderCard(it, onClick = { onOpen(it) }) }
                }
            }
        }
    }
}

/** Stands in for a Post's Thumbnail, which doesn't exist yet. */
@Composable
private fun PlaceholderCard(post: Post, onClick: () -> Unit) {
    Surface(Modifier.aspectRatio(1f).clickable(onClick = onClick), color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
            Text(
                post.title.ifBlank { post.shortcode },
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ToSortScreen(posts: List<Post>, snackbar: SnackbarHostState, onBack: () -> Unit, onOpen: (Post) -> Unit) {
    PostGridScreen(
        title = { Text(stringResource(R.string.to_sort)) },
        posts = posts,
        emptyText = stringResource(R.string.to_sort_empty),
        snackbar = snackbar,
        onBack = onBack,
        onOpen = onOpen,
    )
}

@Preview
@Composable
private fun ToSortScreenPreview() {
    MaterialTheme {
        ToSortScreen(
            listOf("C1a2B3c4D5e", "B9x_Y-z0", "AbCdEf").mapIndexed { i, shortcode ->
                Post(id = i + 1L, shortcode = shortcode, url = "https://www.instagram.com/p/$shortcode/", addedAt = 0)
            },
            remember { SnackbarHostState() },
            onBack = {},
            onOpen = {},
        )
    }
}

@Preview
@Composable
private fun EmptyToSortScreenPreview() {
    MaterialTheme { ToSortScreen(emptyList(), remember { SnackbarHostState() }, onBack = {}, onOpen = {}) }
}

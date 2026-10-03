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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.data.Post
import java.io.File

/**
 * A view of Posts (To sort, a Collection) as a 3-column grid, with an optional [header] above it.
 *
 * @param thumbnailOf the Post's Thumbnail file, or null while it has none.
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
                    items(posts, key = { it.id }) { PostCard(it, thumbnailOf(it), onClick = { onOpen(it) }) }
                }
            }
        }
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

@Composable
fun ToSortScreen(
    posts: List<Post>,
    thumbnailOf: (Post) -> File?,
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
    )
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
        ToSortScreen(emptyList(), thumbnailOf = { null }, remember { SnackbarHostState() }, onBack = {}, onOpen = {})
    }
}

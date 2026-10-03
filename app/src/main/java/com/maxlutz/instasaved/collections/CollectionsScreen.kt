package com.maxlutz.instasaved.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.CollectionWithCount
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.nextColor
import com.maxlutz.instasaved.ui.InstaSavedTheme
import com.maxlutz.instasaved.ui.ScreenTitle
import com.maxlutz.instasaved.ui.SoftButton
import java.io.File

/**
 * The app's home, "Saved": a cover card for All posts, then one per Collection alphabetically. A cover is a mosaic
 * of the latest Thumbnails in it, underlined in the Collection's color.
 *
 * @param allCover the latest Thumbnails of all the Posts, up to 4.
 * @param coverOf the latest Thumbnails of the Posts in a Collection, up to 4.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    allCount: Int,
    allCover: List<File>,
    collections: List<CollectionWithCount>,
    coverOf: (Collection) -> List<File>,
    snackbar: SnackbarHostState,
    onOpenAll: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onCreate: (Collection) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.saved), style = ScreenTitle) },
                actions = {
                    SoftButton(
                        stringResource(R.string.add_collection),
                        onClick = { creating = true },
                        modifier = Modifier.padding(end = 14.dp),
                    )
                },
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "all") {
                Cover(stringResource(R.string.all_posts), allCount, allCover, color = null, onClick = onOpenAll)
            }
            items(collections, key = { it.collection.id }) { (collection, postCount) ->
                Cover(
                    collection.name,
                    postCount,
                    coverOf(collection),
                    color = collection.color,
                    onClick = { onOpenCollection(collection) },
                )
            }
            if (collections.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.no_collections),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (creating) {
        CollectionEditorDialog(
            title = stringResource(R.string.new_collection),
            initial = Collection(name = "", color = nextColor(collections.map { it.collection.color })),
            otherNames = collections.map { it.collection.name },
            onSave = {
                creating = false
                onCreate(it)
            },
            onDismiss = { creating = false },
        )
    }
}

/** A cover card: the mosaic, then the name and how many Posts are behind it. */
@Composable
private fun Cover(name: String, postCount: Int, thumbnails: List<File>, color: Int?, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (row in 0..1) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (column in 0..1) {
                        val thumbnail = thumbnails.getOrNull(row * 2 + column)
                        Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)) {
                            if (thumbnail != null) {
                                AsyncImage(
                                    thumbnail,
                                    contentDescription = null,
                                    Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
            if (color != null) Box(Modifier.fillMaxWidth().height(4.dp).background(Color(color)))
        }
        Text(
            name,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
        Text(
            postCount.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun CollectionsScreenPreview() {
    InstaSavedTheme {
        CollectionsScreen(
            allCount = 23,
            allCover = emptyList(),
            collections = listOf(
                CollectionWithCount(Collection(1, "✈️ Japan", PALETTE[1], "Kyoto first"), 8),
                CollectionWithCount(Collection(2, "🍝 Pasta", PALETTE[0]), 3),
            ),
            coverOf = { emptyList() },
            snackbar = remember { SnackbarHostState() },
            onOpenAll = {},
            onOpenCollection = {},
            onCreate = {},
            bottomBar = {},
        )
    }
}

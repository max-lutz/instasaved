package com.maxlutz.instasaved.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.CollectionWithCount
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.nextColor

/** The app's home: To sort, then every Collection alphabetically, each with its Post count. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    toSortCount: Int,
    collections: List<CollectionWithCount>,
    snackbar: SnackbarHostState,
    onOpenToSort: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onCreate: (Collection) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }) { Text(stringResource(R.string.new_collection)) }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "to-sort") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.to_sort)) },
                    trailingContent = { Text(toSortCount.toString()) },
                    modifier = Modifier.clickable(onClick = onOpenToSort),
                )
                HorizontalDivider()
            }
            if (collections.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.no_collections),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(collections, key = { it.collection.id }) { (collection, postCount) ->
                ListItem(
                    headlineContent = { Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = collection.note.takeIf { it.isNotBlank() }?.let { note ->
                        { Text(note, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    leadingContent = { ColorDot(collection.color, size = 16.dp) },
                    trailingContent = { Text(postCount.toString()) },
                    modifier = Modifier.clickable { onOpenCollection(collection) },
                )
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

@Preview
@Composable
private fun CollectionsScreenPreview() {
    MaterialTheme {
        CollectionsScreen(
            toSortCount = 12,
            collections = listOf(
                CollectionWithCount(Collection(1, "✈️ Japan", PALETTE[1], "Kyoto first"), 8),
                CollectionWithCount(Collection(2, "🍝 Pasta", PALETTE[0]), 3),
            ),
            snackbar = remember { SnackbarHostState() },
            onOpenToSort = {},
            onOpenCollection = {},
            onCreate = {},
        )
    }
}

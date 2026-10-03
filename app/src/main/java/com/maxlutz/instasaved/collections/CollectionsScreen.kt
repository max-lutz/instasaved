package com.maxlutz.instasaved.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.maxlutz.instasaved.data.CollectionWithCount
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Tag
import com.maxlutz.instasaved.data.TagWithCount
import com.maxlutz.instasaved.data.nextColor
import com.maxlutz.instasaved.tags.TagEditorDialog

/**
 * The app's home: All, To sort, then every Collection alphabetically, each with its Post count, then every Tag the
 * same way, then Recently deleted. Tapping a Tag edits it. The Backup menu writes and restores the manual backup
 * file, and starts the Desktop Import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    allCount: Int,
    toSortCount: Int,
    collections: List<CollectionWithCount>,
    snackbar: SnackbarHostState,
    onOpenAll: () -> Unit,
    onOpenToSort: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onCreate: (Collection) -> Unit,
    tags: List<TagWithCount>,
    onCreateTag: (Tag) -> Unit,
    onSaveTag: (Tag) -> Unit,
    recentlyDeletedCount: Int,
    onOpenRecentlyDeleted: () -> Unit,
    onWriteBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onDesktopImport: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var creatingTag by rememberSaveable { mutableStateOf(false) }
    var editingTagId by rememberSaveable { mutableStateOf<Long?>(null) }
    var backupMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box {
                        TextButton(onClick = { backupMenuOpen = true }) { Text(stringResource(R.string.backup)) }
                        DropdownMenu(expanded = backupMenuOpen, onDismissRequest = { backupMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.backup_write)) },
                                onClick = {
                                    backupMenuOpen = false
                                    onWriteBackup()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.backup_restore)) },
                                onClick = {
                                    backupMenuOpen = false
                                    onRestoreBackup()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.desktop_import)) },
                                onClick = {
                                    backupMenuOpen = false
                                    onDesktopImport()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }) { Text(stringResource(R.string.new_collection)) }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "all") {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.all)) },
                    trailingContent = { Text(allCount.toString()) },
                    modifier = Modifier.clickable(onClick = onOpenAll),
                )
            }
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
            item(key = "tags-header") {
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.tags), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { creatingTag = true }) { Text(stringResource(R.string.new_tag)) }
                }
            }
            if (tags.isEmpty()) {
                item(key = "no-tags") {
                    Text(
                        stringResource(R.string.no_tags),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(tags, key = { "tag-${it.tag.id}" }) { (tag, postCount) ->
                ListItem(
                    headlineContent = { Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { ColorDot(tag.color, size = 16.dp) },
                    trailingContent = { Text(postCount.toString()) },
                    modifier = Modifier.clickable { editingTagId = tag.id },
                )
            }
            item(key = "recently-deleted") {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.recently_deleted)) },
                    trailingContent = { Text(recentlyDeletedCount.toString()) },
                    modifier = Modifier.clickable(onClick = onOpenRecentlyDeleted),
                )
            }
        }
    }

    if (creatingTag) {
        TagEditorDialog(
            title = stringResource(R.string.new_tag),
            initial = Tag(name = "", color = nextColor(tags.map { it.tag.color })),
            otherNames = tags.map { it.tag.name },
            onSave = {
                creatingTag = false
                onCreateTag(it)
            },
            onDismiss = { creatingTag = false },
        )
    }
    tags.find { it.tag.id == editingTagId }?.tag?.let { tag ->
        TagEditorDialog(
            title = stringResource(R.string.edit_tag),
            initial = tag,
            otherNames = tags.map { it.tag.name } - tag.name,
            onSave = {
                editingTagId = null
                onSaveTag(it)
            },
            onDismiss = { editingTagId = null },
        )
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
            allCount = 23,
            toSortCount = 12,
            collections = listOf(
                CollectionWithCount(Collection(1, "✈️ Japan", PALETTE[1], "Kyoto first"), 8),
                CollectionWithCount(Collection(2, "🍝 Pasta", PALETTE[0]), 3),
            ),
            snackbar = remember { SnackbarHostState() },
            onOpenAll = {},
            onOpenToSort = {},
            onOpenCollection = {},
            onCreate = {},
            tags = listOf(TagWithCount(Tag(1, "Vegan", PALETTE[3]), 5)),
            onCreateTag = {},
            onSaveTag = {},
            recentlyDeletedCount = 2,
            onOpenRecentlyDeleted = {},
            onWriteBackup = {},
            onRestoreBackup = {},
            onDesktopImport = {},
        )
    }
}

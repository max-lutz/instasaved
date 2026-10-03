package com.maxlutz.instasaved.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.maxlutz.instasaved.collections.ColorDot
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Tag
import com.maxlutz.instasaved.data.TagWithCount
import com.maxlutz.instasaved.data.nextColor
import com.maxlutz.instasaved.ui.InstaSavedTheme
import com.maxlutz.instasaved.ui.SoftButton

/** Every Tag alphabetically, each with how many Posts carry it. Tapping one edits it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    tags: List<TagWithCount>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onCreate: (Tag) -> Unit,
    onSave: (Tag) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tags)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    SoftButton(
                        stringResource(R.string.new_tag),
                        onClick = { creating = true },
                        modifier = Modifier.padding(end = 14.dp),
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (tags.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.no_tags),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
            items(tags, key = { it.tag.id }) { (tag, postCount) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingId = tag.id }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ColorDot(tag.color, size = 16.dp)
                    Text(
                        tag.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(postCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (creating) {
        TagEditorDialog(
            title = stringResource(R.string.new_tag),
            initial = Tag(name = "", color = nextColor(tags.map { it.tag.color })),
            otherNames = tags.map { it.tag.name },
            onSave = {
                creating = false
                onCreate(it)
            },
            onDismiss = { creating = false },
        )
    }
    tags.find { it.tag.id == editingId }?.tag?.let { tag ->
        TagEditorDialog(
            title = stringResource(R.string.edit_tag),
            initial = tag,
            otherNames = tags.map { it.tag.name } - tag.name,
            onSave = {
                editingId = null
                onSave(it)
            },
            onDismiss = { editingId = null },
        )
    }
}

@Preview
@Composable
private fun TagsScreenPreview() {
    InstaSavedTheme {
        TagsScreen(
            tags = listOf(TagWithCount(Tag(1, "Quick", PALETTE[1]), 5), TagWithCount(Tag(2, "Vegan", PALETTE[3]), 2)),
            snackbar = remember { SnackbarHostState() },
            onBack = {},
            onCreate = {},
            onSave = {},
        )
    }
}

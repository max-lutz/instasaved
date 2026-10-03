package com.maxlutz.instasaved.collections

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.data.Collection

/**
 * The menu to put a Post in a Collection: every Collection, then "New Collection…".
 *
 * @param collections every Collection, alphabetically.
 * @param onPick the picked Collection's id, or null for To sort.
 * @param offerToSort whether To sort is a choice too; not for a Post that is already there.
 */
@Composable
fun CollectionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    collections: List<Collection>,
    onPick: (Long?) -> Unit,
    onNew: () -> Unit,
    offerToSort: Boolean,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (offerToSort) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.to_sort)) },
                onClick = {
                    onDismiss()
                    onPick(null)
                },
            )
        }
        collections.forEach { collection ->
            DropdownMenuItem(
                text = { Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { ColorDot(collection.color) },
                onClick = {
                    onDismiss()
                    onPick(collection.id)
                },
            )
        }
        if (offerToSort || collections.isNotEmpty()) HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.new_collection_ellipsis)) },
            onClick = {
                onDismiss()
                onNew()
            },
        )
    }
}

package com.maxlutz.instasaved.deleted

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.grid.PostGridScreen
import java.io.File

/** Recently deleted: the Deleted Posts that can still be restored. Tapping one offers to restore it. */
@Composable
fun RecentlyDeletedScreen(
    posts: List<Post>,
    thumbnailOf: (Post) -> File?,
    snackbar: SnackbarHostState,
    now: Long,
    onBack: () -> Unit,
    onRestore: (Post) -> Unit,
    onEmpty: () -> Unit,
) {
    var restoringId by rememberSaveable { mutableStateOf<Long?>(null) }
    var emptying by rememberSaveable { mutableStateOf(false) }

    PostGridScreen(
        title = { Text(stringResource(R.string.recently_deleted)) },
        posts = posts,
        thumbnailOf = thumbnailOf,
        emptyText = stringResource(R.string.recently_deleted_empty),
        snackbar = snackbar,
        onBack = onBack,
        onOpen = { restoringId = it.id },
        actions = {
            if (posts.isNotEmpty()) {
                TextButton(onClick = { emptying = true }) { Text(stringResource(R.string.empty_now)) }
            }
        },
        header = {
            Text(
                stringResource(R.string.recently_deleted_explained),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
    )

    posts.find { it.id == restoringId }?.let { post ->
        val daysLeft = RecentlyDeleted.daysLeft(post, now)
        AlertDialog(
            onDismissRequest = { restoringId = null },
            title = { Text(post.title.ifBlank { post.shortcode }) },
            text = { Text(pluralStringResource(R.plurals.restore_post_days_left, daysLeft, daysLeft)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        restoringId = null
                        onRestore(post)
                    },
                ) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                TextButton(onClick = { restoringId = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (emptying) {
        AlertDialog(
            onDismissRequest = { emptying = false },
            title = { Text(stringResource(R.string.empty_now_title)) },
            text = { Text(pluralStringResource(R.plurals.empty_now_posts, posts.size, posts.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        emptying = false
                        onEmpty()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.empty_now)) }
            },
            dismissButton = { TextButton(onClick = { emptying = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Preview
@Composable
private fun RecentlyDeletedScreenPreview() {
    MaterialTheme {
        RecentlyDeletedScreen(
            posts = listOf(Post(id = 1, shortcode = "C1a2B3c4D5e", url = "", addedAt = 0, title = "Carbonara", deletedAt = 0)),
            thumbnailOf = { null },
            snackbar = remember { SnackbarHostState() },
            now = 0,
            onBack = {},
            onRestore = {},
            onEmpty = {},
        )
    }
}

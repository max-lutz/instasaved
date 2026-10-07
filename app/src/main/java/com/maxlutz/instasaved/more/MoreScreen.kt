package com.maxlutz.instasaved.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.ui.InstaSavedTheme
import com.maxlutz.instasaved.ui.ScreenTitle

/**
 * Everything that is not browsing Posts: Recently deleted and the Tags, then the manual backup file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    recentlyDeletedCount: Int,
    tagCount: Int,
    snackbar: SnackbarHostState,
    onOpenRecentlyDeleted: () -> Unit,
    onOpenTags: () -> Unit,
    onWriteBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.more), style = ScreenTitle) }) },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            MoreRow(stringResource(R.string.recently_deleted), onOpenRecentlyDeleted, count = recentlyDeletedCount)
            MoreRow(stringResource(R.string.tags), onOpenTags, count = tagCount)
            HorizontalDivider()
            MoreRow(stringResource(R.string.backup_write), onWriteBackup)
            MoreRow(stringResource(R.string.backup_restore), onRestoreBackup)
        }
    }
}

/** A line of the More screen; one leading to a list says how long that list is. */
@Composable
private fun MoreRow(label: String, onClick: () -> Unit, count: Int? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (count != null) Text("$count ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview
@Composable
private fun MoreScreenPreview() {
    InstaSavedTheme {
        MoreScreen(
            recentlyDeletedCount = 3,
            tagCount = 4,
            snackbar = remember { SnackbarHostState() },
            onOpenRecentlyDeleted = {},
            onOpenTags = {},
            onWriteBackup = {},
            onRestoreBackup = {},
            bottomBar = {},
        )
    }
}

package com.maxlutz.instasaved.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.sync.SyncStatus
import com.maxlutz.instasaved.sync.SyncStatusText
import com.maxlutz.instasaved.ui.InstaSavedTheme
import com.maxlutz.instasaved.ui.ScreenTitle
import java.time.LocalDate

/**
 * Everything that is not browsing Posts: Sync now with the sync status, "Mark all as seen" while some Posts are
 * New, Recently deleted and the Tags, the settings, then the manual backup file.
 *
 * @param newCount how many Posts are New.
 * @param showBarePosts whether the covers of the Saved screen say how many of their Posts are Bare Posts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    syncStatus: SyncStatus,
    syncing: Boolean,
    newCount: Int,
    recentlyDeletedCount: Int,
    tagCount: Int,
    showBarePosts: Boolean,
    snackbar: SnackbarHostState,
    onSyncNow: () -> Unit,
    onMarkAllSeen: () -> Unit,
    onOpenRecentlyDeleted: () -> Unit,
    onOpenTags: () -> Unit,
    onShowBarePostsChange: (Boolean) -> Unit,
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
            Column(
                Modifier.fillMaxWidth().clickable(enabled = !syncing, onClick = onSyncNow)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Text(stringResource(R.string.sync_now), style = MaterialTheme.typography.bodyLarge)
                SyncStatusText(syncStatus, syncing)
            }
            if (newCount > 0) {
                MoreRow(
                    stringResource(R.string.mark_all_as_seen),
                    onMarkAllSeen,
                    detail = pluralStringResource(R.plurals.sync_new, newCount, newCount),
                )
            }
            HorizontalDivider()
            MoreRow(stringResource(R.string.recently_deleted), onOpenRecentlyDeleted, count = recentlyDeletedCount)
            MoreRow(stringResource(R.string.tags), onOpenTags, count = tagCount)
            HorizontalDivider()
            SwitchRow(
                stringResource(R.string.show_bare_posts),
                stringResource(R.string.show_bare_posts_explained),
                checked = showBarePosts,
                onCheckedChange = onShowBarePostsChange,
            )
            HorizontalDivider()
            MoreRow(stringResource(R.string.backup_write), onWriteBackup)
            MoreRow(stringResource(R.string.backup_restore), onRestoreBackup)
        }
    }
}

/** A line of the More screen; one leading to a list says how long that list is, an action may say what it is on. */
@Composable
private fun MoreRow(label: String, onClick: () -> Unit, count: Int? = null, detail: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (count != null) Text("$count ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (detail != null) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A setting of the More screen that is on or off: what it is called, what it means under that, and the switch. */
@Composable
private fun SwitchRow(label: String, explained: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                explained,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The whole row toggles it.
        Switch(checked, onCheckedChange = null)
    }
}

@Preview
@Composable
private fun MoreScreenPreview() {
    InstaSavedTheme {
        MoreScreen(
            syncStatus = SyncStatus(syncedAt = System.currentTimeMillis() - 2 * 3_600_000, LocalDate.of(2026, 10, 1)),
            syncing = false,
            newCount = 8,
            recentlyDeletedCount = 3,
            tagCount = 4,
            showBarePosts = true,
            snackbar = remember { SnackbarHostState() },
            onSyncNow = {},
            onMarkAllSeen = {},
            onOpenRecentlyDeleted = {},
            onOpenTags = {},
            onShowBarePostsChange = {},
            onWriteBackup = {},
            onRestoreBackup = {},
            bottomBar = {},
        )
    }
}

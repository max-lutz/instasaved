package com.maxlutz.instasaved.sync

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.ui.warning
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The sync status line: "Synced 2 hours ago · Export from 1 Oct", then what went wrong, if anything did, and the
 * stale warning when the Exports stopped coming.
 *
 * @param short only when the last Sync went, that it failed, or that the Exports are stale: for the home's top bar.
 */
@Composable
fun SyncStatusText(status: SyncStatus, running: Boolean, modifier: Modifier = Modifier, short: Boolean = false) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // "2 minutes ago" goes on counting while the screen is shown.
    LaunchedEffect(Unit) {
        while (true) {
            delay(DateUtils.MINUTE_IN_MILLIS)
            now = System.currentTimeMillis()
        }
    }
    val problem = status.problem
    val stale = status.isStale()
    val synced = status.syncedAt?.let {
        if (now - it < DateUtils.MINUTE_IN_MILLIS) {
            stringResource(R.string.just_now)
        } else {
            DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString()
        }
    }
    val text = when {
        running -> stringResource(R.string.syncing)
        short && problem != null -> stringResource(R.string.sync_failed)
        short && stale -> stringResource(R.string.sync_stale_short)
        short -> synced?.let { stringResource(R.string.synced, it) } ?: stringResource(R.string.not_synced_yet)
        else -> listOfNotNull(
            synced?.let { stringResource(R.string.synced, it) } ?: stringResource(R.string.not_synced_yet),
            status.newestExport?.let { stringResource(R.string.export_from, dayOf(it)) },
            problem?.let { describe(it) },
            if (stale) stringResource(R.string.sync_stale, SyncStatus.STALE_AFTER_DAYS) else null,
        ).joinToString(" · ")
    }
    Text(
        text,
        modifier,
        color = when {
            running -> MaterialTheme.colorScheme.onSurfaceVariant
            problem != null -> MaterialTheme.colorScheme.error
            stale -> MaterialTheme.colorScheme.warning
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        style = MaterialTheme.typography.bodySmall,
        maxLines = if (short) 1 else Int.MAX_VALUE,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun describe(problem: SyncProblem): String = when (problem) {
    SyncProblem.AccessNotGranted -> stringResource(R.string.sync_access_not_granted)
    SyncProblem.AccessRefused -> stringResource(R.string.sync_access_refused)
    SyncProblem.Offline -> stringResource(R.string.sync_offline)
    SyncProblem.NoExport -> stringResource(R.string.sync_no_export)
    is SyncProblem.ExportsFailed -> pluralStringResource(
        R.plurals.sync_exports_failed,
        problem.dates.size,
        problem.dates.joinToString(", ") { dayOf(it) },
    )
}

private val DAY = DateTimeFormatter.ofPattern("d MMM")

private fun dayOf(date: LocalDate): String = date.format(DAY)

/**
 * The Sync Summary, on the home: what the last Sync that changed something did ("Last Sync: 8 new Posts · 2 captions
 * updated"), until the user dismisses it.
 *
 * @param summary not empty.
 */
@Composable
fun SyncSummaryCard(summary: SyncSummary, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val counts = listOfNotNull(
        summary.new.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.sync_new, it, it) },
        summary.captionsUpdated.takeIf { it > 0 }
            ?.let { pluralStringResource(R.plurals.sync_captions_updated, it, it) },
    ).joinToString(" · ")
    val label = stringResource(R.string.last_sync)
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append(label)
                    append(" ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(counts) }
                },
                Modifier.weight(1f).padding(vertical = 8.dp),
                fontSize = 13.sp,
            )
            Surface(onClick = onDismiss, color = Color.Transparent) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    stringResource(R.string.dismiss),
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

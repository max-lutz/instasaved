package com.maxlutz.instasaved

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.updateText
import com.maxlutz.instasaved.detail.PostDetailScreen
import com.maxlutz.instasaved.share.ShareIn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainActivity : ComponentActivity() {
    private val database by lazy { (application as InstaSavedApplication).database }

    // Text is saved on every keystroke; the lock keeps the saves in typing order.
    private val textSaves = Mutex()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Not on recreation (e.g. rotation): the share was already handled.
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            MaterialTheme {
                val posts by database.postDao().observeToSort().collectAsState(initial = emptyList())
                var openPostId by rememberSaveable { mutableStateOf<Long?>(null) }
                val snackbar = remember { SnackbarHostState() }
                val deletedMessage = stringResource(R.string.post_deleted)
                val undoLabel = stringResource(R.string.undo)

                when (val id = openPostId) {
                    null -> ToSortScreen(posts, snackbar, onOpen = { openPostId = it.id })
                    else -> {
                        BackHandler { openPostId = null }
                        val post by remember(id) { database.postDao().observe(id) }.collectAsState(initial = null)
                        post?.let {
                            PostDetailScreen(
                                it,
                                onTextChange = ::saveText,
                                onOpenInInstagram = { openInInstagram(it) },
                                onDelete = {
                                    openPostId = null
                                    delete(it.id) {
                                        snackbar.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Long)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveText(post: Post) {
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            textSaves.withLock { database.postDao().updateText(post) }
        }
    }

    /** Moves the Post to Recently deleted, then restores it if [askUndo] comes back with the Undo action. */
    private fun delete(id: Long, askUndo: suspend () -> SnackbarResult) {
        lifecycleScope.launch {
            database.postDao().delete(id, at = System.currentTimeMillis())
            if (askUndo() == SnackbarResult.ActionPerformed) database.postDao().restore(id)
        }
    }

    /** Opens the link the Post was added from: the Instagram app if installed, else the browser. */
    private fun openInInstagram(post: Post) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, post.url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.nothing_can_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        lifecycleScope.launch {
            val message = when (ShareIn(database.postDao()).receive(text)) {
                is ShareIn.Result.Added -> R.string.share_in_added
                is ShareIn.Result.AlreadySaved -> R.string.share_in_already_saved
                is ShareIn.Result.PreviouslyDeleted -> R.string.share_in_previously_deleted
                ShareIn.Result.NotAPostLink -> R.string.share_in_not_a_post_link
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToSortScreen(posts: List<Post>, snackbar: SnackbarHostState, onOpen: (Post) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.to_sort)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (posts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.to_sort_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(posts, key = { it.id }) { PlaceholderCard(it, onClick = { onOpen(it) }) }
            }
        }
    }
}

/** Stands in for a Post's Thumbnail, which doesn't exist yet. */
@Composable
private fun PlaceholderCard(post: Post, onClick: () -> Unit) {
    Surface(Modifier.aspectRatio(1f).clickable(onClick = onClick), color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
            Text(
                post.title.ifBlank { post.shortcode },
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview
@Composable
private fun ToSortScreenPreview() {
    MaterialTheme {
        ToSortScreen(
            listOf("C1a2B3c4D5e", "B9x_Y-z0", "AbCdEf").mapIndexed { i, shortcode ->
                Post(id = i + 1L, shortcode = shortcode, url = "https://www.instagram.com/p/$shortcode/", addedAt = 0)
            },
            remember { SnackbarHostState() },
            onOpen = {},
        )
    }
}

@Preview
@Composable
private fun EmptyToSortScreenPreview() {
    MaterialTheme { ToSortScreen(emptyList(), remember { SnackbarHostState() }, onOpen = {}) }
}

package com.maxlutz.instasaved

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.share.ShareIn
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val database by lazy { (application as InstaSavedApplication).database }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Not on recreation (e.g. rotation): the share was already handled.
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            MaterialTheme {
                val posts by database.postDao().observeToSort().collectAsState(initial = emptyList())
                ToSortScreen(posts)
            }
        }
    }

    private fun handleShare(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        lifecycleScope.launch {
            val message = when (ShareIn(database.postDao()).receive(text)) {
                is ShareIn.Result.Added -> R.string.share_in_added
                is ShareIn.Result.AlreadySaved -> R.string.share_in_already_saved
                ShareIn.Result.NotAPostLink -> R.string.share_in_not_a_post_link
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToSortScreen(posts: List<Post>) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.to_sort)) }) }) { padding ->
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
                items(posts, key = { it.id }) { PlaceholderCard(it) }
            }
        }
    }
}

/** Stands in for a Post's Thumbnail, which doesn't exist yet. */
@Composable
private fun PlaceholderCard(post: Post) {
    Surface(Modifier.aspectRatio(1f), color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
            Text(post.shortcode, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
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
        )
    }
}

@Preview
@Composable
private fun EmptyToSortScreenPreview() {
    MaterialTheme { ToSortScreen(emptyList()) }
}

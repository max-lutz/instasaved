package com.maxlutz.instasaved

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.maxlutz.instasaved.collections.CollectionScreen
import com.maxlutz.instasaved.collections.CollectionsScreen
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.updateText
import com.maxlutz.instasaved.detail.PostDetailScreen
import com.maxlutz.instasaved.grid.ToSortScreen
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
                val collections by database.collectionDao().observeAll().collectAsState(initial = emptyList())
                val toSort by database.postDao().observeToSort().collectAsState(initial = emptyList())
                var view by rememberSaveable(stateSaver = View.Saver) { mutableStateOf<View>(View.Home) }
                var openPostId by rememberSaveable { mutableStateOf<Long?>(null) }
                val snackbar = remember { SnackbarHostState() }
                val deletedMessage = stringResource(R.string.post_deleted)
                val undoLabel = stringResource(R.string.undo)
                val nameTakenMessage = stringResource(R.string.collection_name_taken)
                val showNameTaken: () -> Unit = { lifecycleScope.launch { snackbar.showSnackbar(nameTakenMessage) } }
                val openPost: (Post) -> Unit = { openPostId = it.id }

                when (val id = openPostId) {
                    null -> when (val shown = view) {
                        View.Home -> CollectionsScreen(
                            toSortCount = toSort.size,
                            collections = collections,
                            snackbar = snackbar,
                            onOpenToSort = { view = View.ToSort },
                            onOpenCollection = { view = View.InCollection(it.id) },
                            onCreate = { createCollection(it, onNameTaken = showNameTaken) },
                        )
                        View.ToSort -> {
                            BackHandler { view = View.Home }
                            ToSortScreen(toSort, snackbar, onBack = { view = View.Home }, onOpen = openPost)
                        }
                        is View.InCollection -> {
                            BackHandler { view = View.Home }
                            val collection = collections.find { it.collection.id == shown.id }?.collection
                            val posts by remember(shown.id) { database.postDao().observeInCollection(shown.id) }
                                .collectAsState(initial = emptyList())
                            // Null until the list loads, and after the Collection is deleted.
                            collection?.let {
                                CollectionScreen(
                                    collection = it,
                                    posts = posts,
                                    otherNames = collections.map { c -> c.collection.name } - it.name,
                                    snackbar = snackbar,
                                    onBack = { view = View.Home },
                                    onOpen = openPost,
                                    onSave = { edited ->
                                        lifecycleScope.launch {
                                            if (!database.collectionDao().update(edited)) showNameTaken()
                                        }
                                    },
                                    onDeleteKeepingPosts = {
                                        view = View.Home
                                        lifecycleScope.launch { database.collectionDao().deleteKeepingPosts(it.id) }
                                    },
                                    onDeleteWithPosts = {
                                        view = View.Home
                                        val at = System.currentTimeMillis()
                                        lifecycleScope.launch { database.collectionDao().deleteWithPosts(it.id, at) }
                                    },
                                )
                            }
                        }
                    }
                    else -> {
                        BackHandler { openPostId = null }
                        val post by remember(id) { database.postDao().observe(id) }.collectAsState(initial = null)
                        post?.let {
                            PostDetailScreen(
                                it,
                                collections = collections.map { c -> c.collection },
                                onTextChange = ::saveText,
                                onCollectionChange = { collectionId ->
                                    lifecycleScope.launch { database.postDao().setCollection(it.id, collectionId) }
                                },
                                onNewCollection = { collection ->
                                    createCollection(collection, onNameTaken = showNameTaken, thenAssign = it.id)
                                },
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

    /** Creates the Collection, then puts the Post [thenAssign] in it, if any. */
    private fun createCollection(collection: Collection, onNameTaken: () -> Unit, thenAssign: Long? = null) {
        lifecycleScope.launch {
            database.withTransaction {
                // The dialog already checks the name; this only fails if it was taken meanwhile.
                val id = database.collectionDao().create(collection.name, collection.color, collection.note)
                    ?: return@withTransaction onNameTaken()
                if (thenAssign != null) database.postDao().setCollection(thenAssign, id)
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

/** Which list of Posts is shown under an opened Post. */
private sealed interface View {
    data object Home : View
    data object ToSort : View
    data class InCollection(val id: Long) : View

    companion object {
        val Saver = Saver<View, Long>(
            save = {
                when (it) {
                    Home -> -1L
                    ToSort -> 0L
                    is InCollection -> it.id
                }
            },
            // Collection ids start at 1.
            restore = {
                when (it) {
                    -1L -> Home
                    0L -> ToSort
                    else -> InCollection(it)
                }
            },
        )
    }
}

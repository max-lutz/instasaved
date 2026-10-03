package com.maxlutz.instasaved

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.maxlutz.instasaved.backup.BackupFormatException
import com.maxlutz.instasaved.collections.CollectionScreen
import com.maxlutz.instasaved.collections.CollectionsScreen
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.Tag
import com.maxlutz.instasaved.data.updateText
import com.maxlutz.instasaved.deleted.PurgeWorker
import com.maxlutz.instasaved.deleted.RecentlyDeletedScreen
import com.maxlutz.instasaved.desktop.DesktopBackupFormatException
import com.maxlutz.instasaved.detail.PostDetailScreen
import com.maxlutz.instasaved.grid.AllScreen
import com.maxlutz.instasaved.grid.Browse
import com.maxlutz.instasaved.grid.Browsing
import com.maxlutz.instasaved.grid.PostSort
import com.maxlutz.instasaved.grid.SearchScreen
import com.maxlutz.instasaved.grid.ToSortScreen
import com.maxlutz.instasaved.more.MoreScreen
import com.maxlutz.instasaved.share.PostLink
import com.maxlutz.instasaved.share.ShareIn
import com.maxlutz.instasaved.tags.TagPickerDialog
import com.maxlutz.instasaved.tags.TagsScreen
import com.maxlutz.instasaved.thumbnails.ThumbnailWorker
import com.maxlutz.instasaved.ui.BottomBar
import com.maxlutz.instasaved.ui.InstaSavedTheme
import com.maxlutz.instasaved.ui.Tab
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val database by lazy { (application as InstaSavedApplication).database }
    private val thumbnailStore by lazy { (application as InstaSavedApplication).thumbnailStore }
    private val recentlyDeleted by lazy { (application as InstaSavedApplication).recentlyDeleted }
    private val backups by lazy { (application as InstaSavedApplication).backups }
    private val desktopImport by lazy { (application as InstaSavedApplication).desktopImport }
    private val shareIn by lazy { ShareIn(database.postDao(), database.recentlyDeletedDao()) }

    // Text is saved on every keystroke; the lock keeps the saves in typing order.
    private val textSaves = Mutex()

    /** The Post just added by Share-in, offered for tagging on the spot. */
    private var quickTagPostId by mutableStateOf<Long?>(null)

    /** The shared link of a Deleted Post, while the user is asked "Add it back?". */
    private var addBackLink by mutableStateOf<PostLink?>(null)

    /** The backup file the user picked, while they are asked whether to replace everything with it. */
    private var restoreFrom by mutableStateOf<Uri?>(null)

    private val pickBackupDestination =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(::writeBackup) }

    // Any type: file managers do not agree on what a .json file is.
    private val pickBackupFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> restoreFrom = uri } }

    private val pickDesktopBackup =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::importDesktopBackup) }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        quickTagPostId?.let { outState.putLong(QUICK_TAG_POST_ID, it) }
        addBackLink?.let { outState.putString(ADD_BACK_URL, it.url) }
        restoreFrom?.let { outState.putString(RESTORE_FROM, it.toString()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState?.containsKey(QUICK_TAG_POST_ID) == true) {
            quickTagPostId = savedInstanceState.getLong(QUICK_TAG_POST_ID)
        }
        addBackLink = savedInstanceState?.getString(ADD_BACK_URL)?.let(PostLink::find)
        restoreFrom = savedInstanceState?.getString(RESTORE_FROM)?.toUri()
        // Not on recreation (e.g. rotation): the share was already handled.
        if (savedInstanceState == null) handleShare(intent)
        ThumbnailWorker.scheduleRetries(this)
        PurgeWorker.scheduleDaily(this)
        // Also on opening the app, so that Recently deleted never shows a Post past its 30 days.
        lifecycleScope.launch { recentlyDeleted.purgeExpired() }
        setContent {
            InstaSavedTheme {
                val collections by database.collectionDao().observeAll().collectAsState(initial = emptyList())
                val all by database.postDao().observeAll().collectAsState(initial = emptyList())
                val toSort by database.postDao().observeToSort().collectAsState(initial = emptyList())
                val tags by database.tagDao().observeAll().collectAsState(initial = emptyList())
                val postTags by database.tagDao().observePostTags().collectAsState(initial = emptyList())
                val deleted by database.recentlyDeletedDao().observe().collectAsState(initial = emptyList())
                val allTags = tags.map { it.tag }
                val withThumbnail by thumbnailStore.shortcodes.collectAsState()
                val thumbnailOf: (Post) -> File? = {
                    if (it.shortcode in withThumbnail) thumbnailStore.file(it.shortcode) else null
                }
                var view by rememberSaveable(stateSaver = View.Saver) { mutableStateOf<View>(View.Home) }
                var openPostId by rememberSaveable { mutableStateOf<Long?>(null) }
                // The search is still there on coming back from a Post, or from another screen.
                var query by rememberSaveable { mutableStateOf("") }
                var sort by rememberSaveable { mutableStateOf(PostSort.Saved) }
                var groupByTag by rememberSaveable { mutableStateOf(false) }
                var tagId by rememberSaveable { mutableStateOf<Long?>(null) }
                var groupCollectionsByTag by rememberSaveable { mutableStateOf(true) }
                // A Tag deleted meanwhile no longer filters anything.
                val shownTagId = tagId?.takeIf { id -> allTags.any { it.id == id } }
                val browsing = Browsing(Browse(query, sort, groupByTag, shownTagId), allTags, postTags) {
                    query = it.query
                    sort = it.sort
                    groupByTag = it.groupByTag
                    tagId = it.tagId
                }
                // The latest Thumbnails behind each cover of the home.
                val covers = remember(all, withThumbnail) {
                    all.groupBy { it.collectionId }.mapValues { (_, posts) -> posts.mapNotNull(thumbnailOf).take(4) }
                }
                val allCover = remember(all, withThumbnail) {
                    all.asSequence().mapNotNull(thumbnailOf).take(4).toList()
                }
                val snackbar = remember { SnackbarHostState() }
                val deletedMessage = stringResource(R.string.post_deleted)
                val undoLabel = stringResource(R.string.undo)
                val nameTakenMessage = stringResource(R.string.collection_name_taken)
                val showNameTaken: () -> Unit = { lifecycleScope.launch { snackbar.showSnackbar(nameTakenMessage) } }
                val tagNameTakenMessage = stringResource(R.string.tag_name_taken)
                val showTagNameTaken: () -> Unit = {
                    lifecycleScope.launch { snackbar.showSnackbar(tagNameTakenMessage) }
                }
                val openPost: (Post) -> Unit = { openPostId = it.id }
                val bottomBar: @Composable (Tab) -> Unit = { tab ->
                    BottomBar(tab, toSortCount = toSort.size) {
                        view = when (it) {
                            Tab.Collections -> View.Home
                            Tab.ToSort -> View.ToSort
                            Tab.Search -> View.Search
                            Tab.More -> View.More
                        }
                    }
                }

                when (val id = openPostId) {
                    null -> when (val shown = view) {
                        View.Home -> CollectionsScreen(
                            allCount = all.size,
                            allCover = allCover,
                            collections = collections,
                            coverOf = { covers[it.id].orEmpty() },
                            snackbar = snackbar,
                            onOpenAll = { view = View.All },
                            onOpenCollection = { view = View.InCollection(it.id) },
                            onCreate = { createCollection(it, onNameTaken = showNameTaken) },
                            bottomBar = { bottomBar(Tab.Collections) },
                        )
                        View.ToSort -> {
                            BackHandler { view = View.Home }
                            ToSortScreen(
                                toSort,
                                thumbnailOf,
                                collections = collections.map { it.collection },
                                snackbar = snackbar,
                                now = remember(toSort) { System.currentTimeMillis() },
                                onOpen = openPost,
                                onFile = { post, collectionId -> setCollection(post.id, collectionId) },
                                onNewCollection = { post, collection ->
                                    createCollection(collection, onNameTaken = showNameTaken, thenAssign = post.id)
                                },
                                bottomBar = { bottomBar(Tab.ToSort) },
                            )
                        }
                        View.Search -> {
                            BackHandler { view = View.Home }
                            SearchScreen(all, thumbnailOf, browsing, snackbar, onOpen = openPost) {
                                bottomBar(Tab.Search)
                            }
                        }
                        View.More -> {
                            BackHandler { view = View.Home }
                            MoreScreen(
                                recentlyDeletedCount = deleted.size,
                                tagCount = tags.size,
                                snackbar = snackbar,
                                onOpenRecentlyDeleted = { view = View.RecentlyDeleted },
                                onOpenTags = { view = View.Tags },
                                onWriteBackup = {
                                    pickBackupDestination.launch("instasaved-backup-${LocalDate.now()}.json")
                                },
                                onRestoreBackup = { pickBackupFile.launch(arrayOf("*/*")) },
                                onDesktopImport = { pickDesktopBackup.launch(arrayOf("*/*")) },
                                bottomBar = { bottomBar(Tab.More) },
                            )
                        }
                        View.Tags -> {
                            BackHandler { view = View.More }
                            TagsScreen(
                                tags = tags,
                                snackbar = snackbar,
                                onBack = { view = View.More },
                                onCreate = { createTag(it, onNameTaken = showTagNameTaken) },
                                onSave = { edited ->
                                    lifecycleScope.launch {
                                        if (!database.tagDao().update(edited)) showTagNameTaken()
                                    }
                                },
                            )
                        }
                        View.RecentlyDeleted -> {
                            BackHandler { view = View.More }
                            RecentlyDeletedScreen(
                                posts = deleted,
                                thumbnailOf = thumbnailOf,
                                snackbar = snackbar,
                                now = remember(deleted) { System.currentTimeMillis() },
                                onBack = { view = View.More },
                                onRestore = {
                                    lifecycleScope.launch { database.recentlyDeletedDao().restore(it.id) }
                                },
                                onEmpty = { lifecycleScope.launch { recentlyDeleted.empty() } },
                            )
                        }
                        View.All -> {
                            BackHandler { view = View.Home }
                            AllScreen(all, thumbnailOf, snackbar, onBack = { view = View.Home }, onOpen = openPost)
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
                                    thumbnailOf = thumbnailOf,
                                    tags = allTags,
                                    postTags = postTags,
                                    groupByTag = groupCollectionsByTag,
                                    onGroupByTagChange = { groupCollectionsByTag = it },
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
                        val postTags by remember(id) { database.tagDao().observeOnPost(id) }
                            .collectAsState(initial = emptyList())
                        post?.let {
                            PostDetailScreen(
                                it,
                                collections = collections.map { c -> c.collection },
                                onBack = { openPostId = null },
                                onTextChange = ::saveText,
                                onCollectionChange = { collectionId -> setCollection(it.id, collectionId) },
                                onNewCollection = { collection ->
                                    createCollection(collection, onNameTaken = showNameTaken, thenAssign = it.id)
                                },
                                tags = allTags,
                                postTags = postTags,
                                onAddTag = { tag -> addTag(it.id, tag) },
                                onRemoveTag = { tag -> removeTag(it.id, tag) },
                                onNewTag = { tag -> createTag(tag, onNameTaken = showTagNameTaken, thenAddTo = it.id) },
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

                quickTagPostId?.let { id ->
                    val postTags by remember(id) { database.tagDao().observeOnPost(id) }
                        .collectAsState(initial = emptyList())
                    TagPickerDialog(
                        title = stringResource(R.string.share_in_added),
                        message = stringResource(R.string.share_in_tag_it),
                        tags = allTags,
                        onPost = postTags,
                        onAdd = { addTag(id, it) },
                        onRemove = { removeTag(id, it) },
                        onNew = { createTag(it, onNameTaken = showTagNameTaken, thenAddTo = id) },
                        onDismiss = { quickTagPostId = null },
                    )
                }

                addBackLink?.let { link ->
                    AlertDialog(
                        onDismissRequest = { addBackLink = null },
                        title = { Text(stringResource(R.string.share_in_add_back_title)) },
                        text = { Text(stringResource(R.string.share_in_add_back)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    addBackLink = null
                                    lifecycleScope.launch { show(shareIn.addBack(link)) }
                                },
                            ) { Text(stringResource(R.string.add_back)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { addBackLink = null }) { Text(stringResource(R.string.cancel)) }
                        },
                    )
                }

                restoreFrom?.let { uri ->
                    AlertDialog(
                        onDismissRequest = { restoreFrom = null },
                        title = { Text(stringResource(R.string.backup_restore_title)) },
                        text = { Text(stringResource(R.string.backup_restore_explained)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    restoreFrom = null
                                    restoreBackup(uri)
                                },
                            ) { Text(stringResource(R.string.restore)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { restoreFrom = null }) { Text(stringResource(R.string.cancel)) }
                        },
                    )
                }
            }
        }
    }

    private fun writeBackup(to: Uri) {
        lifecycleScope.launch {
            val message = try {
                backups.write(contentResolver.openOutputStream(to) ?: throw IOException("No stream for $to"))
                R.string.backup_written
            } catch (_: IOException) {
                R.string.backup_write_failed
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    /** Wipe-and-replace from the backup file, or tells why nothing was changed. */
    private fun restoreBackup(from: Uri) {
        lifecycleScope.launch {
            val message = try {
                backups.restore(contentResolver.openInputStream(from) ?: throw IOException("No stream for $from"))
                // The Backup keeps deletion dates: a Post may be past its 30 days by now (ADR-0012).
                recentlyDeleted.purgeExpired()
                // A Backup never has Thumbnails.
                ThumbnailWorker.downloadNow(this@MainActivity)
                R.string.backup_restored
            } catch (e: BackupFormatException) {
                if (e.fromNewerApp) R.string.backup_from_newer_app else R.string.backup_not_a_backup
            } catch (_: IOException) {
                R.string.backup_read_failed
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    /** Adds the Socials Organizer backup to what the app holds, or tells why nothing was changed. */
    private fun importDesktopBackup(from: Uri) {
        lifecycleScope.launch {
            val message = try {
                val summary = desktopImport.import(
                    contentResolver.openInputStream(from) ?: throw IOException("No stream for $from"),
                )
                // The desktop app has no Thumbnails.
                ThumbnailWorker.downloadNow(this@MainActivity)
                getString(R.string.desktop_import_done, summary.added, summary.completed, summary.skipped)
            } catch (_: DesktopBackupFormatException) {
                getString(R.string.desktop_import_not_a_backup)
            } catch (_: IOException) {
                getString(R.string.backup_read_failed)
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    /** Creates the Collection, then puts the Post [thenAssign] in it, if any. */
    private fun createCollection(collection: Collection, onNameTaken: () -> Unit, thenAssign: Long? = null) {
        lifecycleScope.launch {
            database.withTransaction {
                // The dialog already checks the name; this only fails if it was taken meanwhile.
                val id = database.collectionDao().create(collection.name, collection.color, collection.note)
                    ?: return@withTransaction onNameTaken()
                if (thenAssign != null) database.postDao().setCollection(thenAssign, id, at = now())
            }
        }
    }

    /** Creates the Tag, then puts it on the Post [thenAddTo], if any. */
    private fun createTag(tag: Tag, onNameTaken: () -> Unit, thenAddTo: Long? = null) {
        lifecycleScope.launch {
            database.withTransaction {
                // The dialog already checks the name; this only fails if it was taken meanwhile.
                val id = database.tagDao().create(tag.name, tag.color) ?: return@withTransaction onNameTaken()
                if (thenAddTo != null) database.tagDao().addToPost(thenAddTo, id, at = now())
            }
        }
    }

    private fun setCollection(postId: Long, collectionId: Long?) {
        lifecycleScope.launch { database.postDao().setCollection(postId, collectionId, at = now()) }
    }

    // The picker stops offering a 5th Tag; the DAO refuses one anyway.
    private fun addTag(postId: Long, tag: Tag) {
        lifecycleScope.launch { database.tagDao().addToPost(postId, tag.id, at = now()) }
    }

    private fun removeTag(postId: Long, tag: Tag) {
        lifecycleScope.launch { database.tagDao().removeFromPost(postId, tag.id, at = now()) }
    }

    private fun saveText(post: Post) {
        lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            textSaves.withLock { database.postDao().updateText(post, at = now()) }
        }
    }

    /** Moves the Post to Recently deleted, then restores it if [askUndo] comes back with the Undo action. */
    private fun delete(id: Long, askUndo: suspend () -> SnackbarResult) {
        lifecycleScope.launch {
            database.postDao().delete(id, at = System.currentTimeMillis())
            if (askUndo() == SnackbarResult.ActionPerformed) database.recentlyDeletedDao().restore(id)
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
        lifecycleScope.launch { show(shareIn.receive(text)) }
    }

    /** Tells the user what became of a share, or asks what is still theirs to decide. */
    private fun show(result: ShareIn.Result) {
        val message = when (result) {
            // Said by the quick-tag step instead.
            is ShareIn.Result.Added -> {
                ThumbnailWorker.downloadNow(this)
                quickTagPostId = result.post.id
                return
            }
            is ShareIn.Result.PreviouslyDeleted -> {
                addBackLink = result.link
                return
            }
            is ShareIn.Result.Restored -> {
                // Usually it kept its Thumbnail; this covers the one that never got it.
                ThumbnailWorker.downloadNow(this)
                R.string.share_in_restored
            }
            is ShareIn.Result.AlreadySaved -> R.string.share_in_already_saved
            ShareIn.Result.NotAPostLink -> R.string.share_in_not_a_post_link
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

private fun now() = System.currentTimeMillis()

private const val QUICK_TAG_POST_ID = "quickTagPostId"
private const val ADD_BACK_URL = "addBackUrl"
private const val RESTORE_FROM = "restoreFrom"

/** Which screen is shown under an opened Post: one of the bottom bar's four, or one reached from them. */
private sealed interface View {
    data object Home : View
    data object ToSort : View
    data object Search : View
    data object More : View
    data object All : View
    data object Tags : View
    data object RecentlyDeleted : View
    data class InCollection(val id: Long) : View

    companion object {
        val Saver = Saver<View, Long>(
            save = {
                when (it) {
                    Tags -> -6L
                    More -> -5L
                    Search -> -4L
                    All -> -3L
                    RecentlyDeleted -> -2L
                    Home -> -1L
                    ToSort -> 0L
                    is InCollection -> it.id
                }
            },
            // Collection ids start at 1.
            restore = {
                when (it) {
                    -6L -> Tags
                    -5L -> More
                    -4L -> Search
                    -3L -> All
                    -2L -> RecentlyDeleted
                    -1L -> Home
                    0L -> ToSort
                    else -> InCollection(it)
                }
            },
        )
    }
}

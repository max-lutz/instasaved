package com.maxlutz.instasaved.collections

import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
import coil3.compose.AsyncImage
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.CollectionWithCount
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.nextColor
import com.maxlutz.instasaved.ui.InstaSavedTheme
import com.maxlutz.instasaved.ui.ScreenTitle
import com.maxlutz.instasaved.ui.SoftButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * The app's home, "Saved": a cover card for All posts, then one per Collection alphabetically. A cover is a mosaic
 * of the latest Thumbnails in it, underlined in the Collection's color. Dragging a Collection's cover onto another's
 * asks to move all its Posts there.
 *
 * @param allCover the latest Thumbnails of all the Posts, up to 4.
 * @param coverOf the latest Thumbnails of the Posts in a Collection, up to 4.
 * @param onMoveAll the Collection to move all the Posts from, the one to move them to, and whether to delete the
 *   first along the way.
 * @param onNoPostsToMove a Collection with no Post to move was dropped on another.
 * @param syncStatus the short sync status, in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    allCount: Int,
    allCover: List<File>,
    collections: List<CollectionWithCount>,
    coverOf: (Collection) -> List<File>,
    snackbar: SnackbarHostState,
    onOpenAll: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onCreate: (Collection) -> Unit,
    onMoveAll: (Collection, Collection, Boolean) -> Unit,
    onNoPostsToMove: (Collection) -> Unit,
    bottomBar: @Composable () -> Unit,
    syncStatus: @Composable () -> Unit = {},
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    // The ids of the Collection dropped and of the one it was dropped on.
    var move by rememberSaveable { mutableStateOf<Pair<Long, Long>?>(null) }
    // Null once either is deleted, which closes the dialog.
    val from = collections.find { it.collection.id == move?.first }
    val to = collections.find { it.collection.id == move?.second }?.collection

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.saved), style = ScreenTitle) },
                actions = {
                    Box(Modifier.padding(end = 10.dp)) { syncStatus() }
                    SoftButton(
                        stringResource(R.string.add_collection),
                        onClick = { creating = true },
                        modifier = Modifier.padding(end = 14.dp),
                    )
                },
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        CoverGrid(
            allCount,
            allCover,
            collections,
            coverOf,
            onOpenAll,
            onOpenCollection,
            onDrop = { dropped, on ->
                if (dropped.postCount == 0) onNoPostsToMove(dropped.collection) else move = dropped.collection.id to on.id
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }

    if (creating) {
        CollectionEditorDialog(
            title = stringResource(R.string.new_collection),
            initial = Collection(name = "", color = nextColor(collections.map { it.collection.color })),
            otherNames = collections.map { it.collection.name },
            onSave = {
                creating = false
                onCreate(it)
            },
            onDismiss = { creating = false },
        )
    }
    if (from != null && to != null) {
        MoveAllPostsDialog(
            from = from.collection.name,
            to = to.name,
            postCount = from.postCount,
            onMove = {
                move = null
                onMoveAll(from.collection, to, false)
            },
            onMoveAndDelete = {
                move = null
                onMoveAll(from.collection, to, true)
            },
            onDismiss = { move = null },
        )
    }
}

/**
 * The grid of covers. A long-press lifts a Collection's cover, which then follows the finger; the grid scrolls
 * while it is held near the top or bottom edge, and the cover under the finger is highlighted.
 *
 * @param onDrop the Collection whose cover was dropped, and the one it was dropped on.
 */
@Composable
private fun CoverGrid(
    allCount: Int,
    allCover: List<File>,
    collections: List<CollectionWithCount>,
    coverOf: (Collection) -> List<File>,
    onOpenAll: () -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onDrop: (CollectionWithCount, Collection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val currentCollections by rememberUpdatedState(collections)
    val currentOnDrop by rememberUpdatedState(onDrop)
    // Where things are, in the window: the grid, and the covers of the Collections on screen, by Collection id.
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val bounds = remember { mutableMapOf<Long, Rect>() }
    var lift by remember { mutableStateOf<Lift?>(null) }
    var targetId by remember { mutableStateOf<Long?>(null) }
    var returning by remember { mutableStateOf<Job?>(null) }

    fun coverAt(position: Offset, except: Long? = null): Long? {
        if (position !in viewport) return null
        return bounds.entries.firstOrNull { (id, cover) -> id != except && position in cover }?.key
    }

    // The cover goes back to its place, whatever comes of the drop: the Posts only move once the dialog says so.
    fun release(dropped: Boolean) {
        val released = lift ?: return
        released.held = false
        targetId = null
        val id = released.item.collection.id
        val on = currentCollections.find { it.collection.id == coverAt(released.finger, except = id) }
        if (dropped && on != null) currentOnDrop(released.item, on.collection)
        returning = scope.launch {
            // Scrolled away meanwhile: nowhere on screen to go back to.
            bounds[id]?.let { home ->
                animate(Offset.VectorConverter, released.topLeft, home.topLeft) { value, _ -> released.topLeft = value }
            }
            lift = null
        }
    }

    val held = lift?.takeIf { it.held }
    LaunchedEffect(held) {
        if (held == null) return@LaunchedEffect
        val edge = with(density) { SCROLL_EDGE.toPx() }
        val maxSpeed = with(density) { SCROLL_SPEED.toPx() }
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            // Dropped since the last frame: nothing is a target any more.
            if (!held.held) break
            val seconds = (now - last) / 1e9f
            last = now
            // How far into the top (negative) or bottom edge the finger is, as a fraction of the edge.
            val y = held.finger.y
            val depth = when {
                y < viewport.top + edge -> (y - viewport.top - edge) / edge
                y > viewport.bottom - edge -> (y - viewport.bottom + edge) / edge
                else -> 0f
            }.coerceIn(-1f, 1f)
            if (depth != 0f) gridState.scrollBy(depth * maxSpeed * seconds)
            // Every frame: scrolling changes what is under a finger that does not move.
            targetId = coverAt(held.finger, except = held.item.collection.id)
        }
    }

    Box(
        modifier
            .onGloballyPositioned { viewport = it.boundsInWindow() }
            .pointerInput(Unit) {
                detectLiftAndDrag(
                    onLift = { position ->
                        val at = position + viewport.topLeft
                        val id = coverAt(at)
                        val item = currentCollections.find { it.collection.id == id }
                        val home = bounds[id]
                        if (item != null && home != null) {
                            returning?.cancel()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            lift = Lift(item, home, at)
                        }
                        item != null && home != null
                    },
                    onDrag = { position -> lift?.moveTo(position + viewport.topLeft) },
                    onDrop = { release(dropped = true) },
                    onCancel = { release(dropped = false) },
                )
            },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "all") {
                Cover(
                    stringResource(R.string.all_posts),
                    allCount,
                    allCover,
                    color = null,
                    modifier = Modifier.clickable(onClick = onOpenAll),
                )
            }
            items(collections, key = { it.collection.id }) { (collection, postCount) ->
                val id = collection.id
                DisposableEffect(id) { onDispose { bounds.remove(id) } }
                Cover(
                    collection.name,
                    postCount,
                    coverOf(collection),
                    color = collection.color,
                    modifier = Modifier
                        .onGloballyPositioned { bounds[id] = Rect(it.positionInWindow(), it.size.toSize()) }
                        .alpha(if (lift?.item?.collection?.id == id) 0.3f else 1f)
                        .clickable { onOpenCollection(collection) },
                    highlighted = targetId == id,
                )
            }
            if (collections.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.no_collections),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        lift?.let {
            Cover(
                it.item.collection.name,
                it.item.postCount,
                coverOf(it.item.collection),
                color = it.item.collection.color,
                modifier = Modifier
                    .offset { (it.topLeft - viewport.topLeft).round() }
                    .width(with(density) { it.width.toDp() })
                    // Smaller than the cover under it, so that its highlight shows around.
                    .graphicsLayer {
                        scaleX = 0.85f
                        scaleY = 0.85f
                        alpha = 0.85f
                    },
            )
        }
    }
}

/** A Collection's cover lifted off the grid, in the window: where it is, and where the finger holding it is. */
private class Lift(val item: CollectionWithCount, home: Rect, finger: Offset) {
    val width = home.width
    private val grip = finger - home.topLeft
    var topLeft by mutableStateOf(home.topLeft)
    var finger by mutableStateOf(finger)

    /** False once dropped, while the cover goes back to its place. */
    var held by mutableStateOf(true)

    fun moveTo(position: Offset) {
        finger = position
        topLeft = position - grip
    }
}

// The finger is in the top or bottom edge of the grid when this close to it; the grid scrolls faster the deeper in.
private val SCROLL_EDGE = 72.dp

// Per second, at the deepest.
private val SCROLL_SPEED = 900.dp

/**
 * A long-press where [onLift] finds something to lift, then a drag of it until the finger goes up. Everything is
 * read before what is under the finger reads it, and taken from it once lifted: the drag does not scroll the grid,
 * and lifting the finger does not tap. A tap, or a move before the long-press, is left alone.
 */
private suspend fun PointerInputScope.detectLiftAndDrag(
    onLift: (Offset) -> Boolean,
    onDrag: (Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    // Null when the long-press time ran out with the finger still down, and in place.
    val gaveUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) break
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
        }
    }
    if (gaveUp != null || !onLift(down.position)) return@awaitEachGesture
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) break
            onDrag(change.position)
        }
    } catch (e: CancellationException) {
        onCancel()
        throw e
    }
    onDrop()
}

/** A cover card: the mosaic, then the name and how many Posts are behind it. [highlighted] as the drop target. */
@Composable
private fun Cover(
    name: String,
    postCount: Int,
    thumbnails: List<File>,
    color: Int?,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Column(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (highlighted) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (row in 0..1) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (column in 0..1) {
                        val thumbnail = thumbnails.getOrNull(row * 2 + column)
                        Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)) {
                            if (thumbnail != null) {
                                AsyncImage(
                                    thumbnail,
                                    contentDescription = null,
                                    Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
            }
            if (color != null) Box(Modifier.fillMaxWidth().height(4.dp).background(Color(color)))
        }
        Text(
            name,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
        Text(
            postCount.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun CollectionsScreenPreview() {
    InstaSavedTheme {
        CollectionsScreen(
            allCount = 23,
            allCover = emptyList(),
            collections = listOf(
                CollectionWithCount(Collection(1, "✈️ Japan", PALETTE[1], "Kyoto first"), 8),
                CollectionWithCount(Collection(2, "🍝 Pasta", PALETTE[0]), 3),
            ),
            coverOf = { emptyList() },
            snackbar = remember { SnackbarHostState() },
            onOpenAll = {},
            onOpenCollection = {},
            onCreate = {},
            onMoveAll = { _, _, _ -> },
            onNoPostsToMove = {},
            bottomBar = {},
        )
    }
}

package com.maxlutz.instasaved.grid

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.ui.Pill

/**
 * A view's Bare Posts, and whether the view shows only those.
 *
 * @param posts the Posts of the view, in its order.
 * @param postTags which Posts carry which Tags.
 * @param offered whether the view offers the filter: "Show Bare Posts" is on.
 * @param on whether the user turned the filter on.
 */
class BareFilter(posts: List<Post>, postTags: List<PostTag>, offered: Boolean, on: Boolean) {
    /** How many of the view's Posts are Bare Posts; 0 when the filter is not [offered]. */
    val count: Int

    /** Whether only the Bare Posts are shown, which takes one to show: never an empty filtered view. */
    val active: Boolean

    /** The Posts to show: the Bare Posts while [active], else all of them. */
    val shown: List<Post>

    init {
        val tagged = postTags.mapTo(HashSet()) { it.postId }
        // The same Posts as IS_BARE in SQL, which the counts on the covers come from.
        val bare = if (offered) {
            posts.filter { it.deletedAt == null && it.id !in tagged && it.postNote.trim(' ', '\t', '\n', '\r').isEmpty() }
        } else {
            emptyList()
        }
        count = bare.size
        active = on && bare.isNotEmpty()
        shown = if (active) bare else posts
    }
}

/**
 * The [BareFilter] of a view, turned off for good once it is on with no Bare Post left to show: a Bare Post turning
 * up later does not filter the view again.
 */
@Composable
fun rememberBareFilter(
    posts: List<Post>,
    postTags: List<PostTag>,
    offered: Boolean,
    on: Boolean,
    onChange: (Boolean) -> Unit,
): BareFilter {
    val filter = remember(posts, postTags, offered, on) { BareFilter(posts, postTags, offered, on) }
    if (on && !filter.active) LaunchedEffect(Unit) { onChange(false) }
    return filter
}

/** "Bare · 3": shows only the view's Bare Posts, then the whole view again. */
@Composable
fun BarePill(filter: BareFilter, onChange: (Boolean) -> Unit) {
    Pill(onClick = { onChange(!filter.active) }, selected = filter.active) {
        Text(stringResource(R.string.bare_count, filter.count), style = MaterialTheme.typography.labelLarge)
    }
}

package com.maxlutz.instasaved.grid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.collections.CollectionEditorDialog
import com.maxlutz.instasaved.collections.CollectionMenu
import com.maxlutz.instasaved.collections.SectionChoice
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.PostTag
import com.maxlutz.instasaved.data.Tag
import com.maxlutz.instasaved.data.nextColor
import com.maxlutz.instasaved.tags.TagPickerDialog
import com.maxlutz.instasaved.tags.Tagging
import com.maxlutz.instasaved.tags.tagIdsInCollectionOf
import com.maxlutz.instasaved.ui.SoftButton

/**
 * What a view of Posts needs to tag, move or delete several of them together.
 *
 * @param collections every Collection, alphabetically, to move the Posts to.
 * @param tags every Tag, alphabetically.
 * @param postTags which Posts carry which Tags.
 * @param posts every Post, to know the Tags used in a Collection.
 * @param onTag a Tag tapped in the picker, to put on the Posts or take off them, see [Tagging].
 * @param onNewTag a Tag created from the picker, to create and put on the Posts.
 * @param onMove the Collection's id to move the Posts to, or null for To sort.
 * @param onMoveToNew a Collection created from the Move menu, to create and move the Posts to.
 * @param sections what creating that Collection needs to put it in a Section.
 */
class BulkActions(
    val collections: List<Collection> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val postTags: List<PostTag> = emptyList(),
    val posts: List<Post> = emptyList(),
    val onTag: (List<Post>, Tag) -> Unit = { _, _ -> },
    val onNewTag: (List<Post>, Tag) -> Unit = { _, _ -> },
    val onMove: (List<Post>, Long?) -> Unit = { _, _ -> },
    val onMoveToNew: (List<Post>, Collection) -> Unit = { _, _ -> },
    val onDelete: (List<Post>) -> Unit = {},
    val sections: SectionChoice = SectionChoice(),
)

/** The Posts picked in a view to act on together. A Post is picked only while the view shows it. */
class Selection internal constructor(
    private val shown: List<Post>,
    picked: Set<Long>,
    private val onChange: (Set<Long>) -> Unit,
) {
    /** The picked Posts, in the order the view shows them. */
    val posts: List<Post> = shown.filter { it.id in picked }
    private val ids = posts.mapTo(HashSet()) { it.id }

    /** Whether the view is selecting: a tap on a Post then picks it instead of opening it. */
    val active get() = posts.isNotEmpty()

    operator fun contains(post: Post) = post.id in ids

    fun toggle(post: Post) = onChange(if (post.id in ids) ids - post.id else ids + post.id)

    fun selectAll() = onChange(shown.mapTo(HashSet()) { it.id })

    fun clear() = onChange(emptySet())
}

/** The [Selection] of a view showing the Posts [shown], empty until a long-press picks one. */
@Composable
internal fun rememberSelection(shown: List<Post>): Selection {
    var picked by rememberSaveable(stateSaver = IdsSaver) { mutableStateOf(emptySet()) }
    return remember(shown, picked) { Selection(shown, picked) { picked = it } }
}

private val IdsSaver = Saver<Set<Long>, LongArray>(save = { it.toLongArray() }, restore = { it.toSet() })

/**
 * The top bar of a view while it is selecting: how many Posts are picked, above Select all, Tags, Move and Delete.
 * Back leaves the selection without acting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(selection: Selection, bulk: BulkActions) {
    val posts = selection.posts
    var tagging by rememberSaveable { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = selection::clear)
    Column {
        TopAppBar(
            title = { Text(pluralStringResource(R.plurals.post_count, posts.size, posts.size)) },
            navigationIcon = {
                IconButton(onClick = selection::clear) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.leave_selection))
                }
            },
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SoftButton(stringResource(R.string.select_all), onClick = selection::selectAll)
            SoftButton(stringResource(R.string.tags), onClick = { tagging = true })
            Box {
                SoftButton(stringResource(R.string.move), onClick = { moving = true })
                CollectionMenu(
                    expanded = moving,
                    onDismiss = { moving = false },
                    collections = bulk.collections,
                    onPick = {
                        bulk.onMove(posts, it)
                        selection.clear()
                    },
                    onNew = { creating = true },
                    offerToSort = true,
                )
            }
            SoftButton(
                stringResource(R.string.delete),
                onClick = {
                    bulk.onDelete(posts)
                    selection.clear()
                },
                danger = true,
            )
        }
    }

    if (tagging) {
        TagPickerDialog(
            title = pluralStringResource(R.plurals.tags_of_posts, posts.size, posts.size),
            tags = bulk.tags,
            tagging = remember(posts, bulk.postTags) { Tagging(posts.map { it.id }, bulk.postTags) },
            inCollection = remember(posts, bulk.posts, bulk.postTags) {
                tagIdsInCollectionOf(posts, bulk.posts, bulk.postTags)
            },
            onTap = { bulk.onTag(posts, it) },
            onNew = { bulk.onNewTag(posts, it) },
            onDismiss = { tagging = false },
        )
    }
    if (creating) {
        CollectionEditorDialog(
            title = stringResource(R.string.new_collection),
            initial = Collection(name = "", color = nextColor(bulk.collections.map { it.color })),
            otherNames = bulk.collections.map { it.name },
            sections = bulk.sections,
            onSave = {
                creating = false
                bulk.onMoveToNew(posts, it)
                selection.clear()
            },
            onDismiss = { creating = false },
        )
    }
}

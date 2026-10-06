package com.maxlutz.instasaved.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxlutz.instasaved.R
import com.maxlutz.instasaved.collections.CollectionEditorDialog
import com.maxlutz.instasaved.collections.CollectionMenu
import com.maxlutz.instasaved.collections.ColorDot
import com.maxlutz.instasaved.collections.SectionChoice
import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Post
import com.maxlutz.instasaved.data.Tag
import com.maxlutz.instasaved.data.editDescription
import com.maxlutz.instasaved.data.editPostNote
import com.maxlutz.instasaved.data.editTitle
import com.maxlutz.instasaved.data.nextColor
import com.maxlutz.instasaved.tags.TagChip
import com.maxlutz.instasaved.tags.TagPickerDialog
import com.maxlutz.instasaved.ui.InstaSavedTheme
import com.maxlutz.instasaved.ui.Pill
import com.maxlutz.instasaved.ui.PlainTextField
import com.maxlutz.instasaved.ui.SoftButton

/**
 * An opened Post, laid out like an Instagram post: its owner above its Embed, then its Collection, Title and
 * Description, Tags and Post Note. The texts are edited where they are read; every edit is handed to
 * [onTextChange] as the whole edited Post, flags included.
 *
 * @param collections every Collection, alphabetically, to pick the Post's from.
 * @param onCollectionChange the picked Collection's id, or null for To sort.
 * @param onNewCollection a Collection created from the picker, to create and put the Post in.
 * @param sections what creating that Collection needs to put it in a Section.
 * @param tags every Tag, alphabetically, to pick the Post's from.
 * @param postTags the Tags the Post carries, alphabetically.
 * @param collectionTagIds the ids of the Tags used in the Post's Collection, which the Tag picker shows first.
 * @param onNewTag a Tag created from the picker, to create and put on the Post.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    post: Post,
    collections: List<Collection>,
    onBack: () -> Unit,
    onTextChange: (Post) -> Unit,
    onCollectionChange: (Long?) -> Unit,
    onNewCollection: (Collection) -> Unit,
    sections: SectionChoice,
    tags: List<Tag>,
    postTags: List<Tag>,
    onAddTag: (Tag) -> Unit,
    onRemoveTag: (Tag) -> Unit,
    onNewTag: (Tag) -> Unit,
    onOpenInInstagram: () -> Unit,
    onDelete: () -> Unit,
    collectionTagIds: Set<Long> = emptySet(),
    showEmbed: Boolean = true,
) {
    // Edits live here and are only written out: the stored Post lags behind while saves are in flight.
    var draft by remember(post.id) { mutableStateOf(post) }
    fun edit(change: Post.() -> Post) {
        val edited = draft.change()
        if (edited != draft) {
            draft = edited
            onTextChange(edited)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // The owner is only known once Sync has seen the Post.
                    Text(
                        post.ownerName.ifBlank { post.ownerUsername },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    SoftButton(
                        stringResource(R.string.open_in_instagram),
                        onClick = onOpenInInstagram,
                        modifier = Modifier.padding(end = 14.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())) {
            // Drags on the Embed scroll only the Embed, so it never fills the screen: the rest scrolls the page.
            val embedHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
            if (showEmbed) Embed(post.shortcode, Modifier.fillMaxWidth().height(embedHeight))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CollectionPicker(
                    current = collections.find { it.id == post.collectionId },
                    collections = collections,
                    onPick = onCollectionChange,
                    onNew = onNewCollection,
                    sections = sections,
                    modifier = Modifier.weight(1f),
                )
                SoftButton(stringResource(R.string.delete), onClick = onDelete, danger = true)
            }
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlainTextField(
                        value = draft.title,
                        onValueChange = { edit { editTitle(it) } },
                        placeholder = stringResource(R.string.title),
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PlainTextField(
                        value = draft.description,
                        onValueChange = { edit { editDescription(it) } },
                        placeholder = stringResource(R.string.description),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                PostTags(tags, postTags, collectionTagIds, onAddTag, onRemoveTag, onNewTag)
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            stringResource(R.string.post_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PlainTextField(
                            value = draft.postNote,
                            onValueChange = { edit { editPostNote(it) } },
                            placeholder = stringResource(R.string.post_note_hint),
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The Post's Collection (or To sort) as a chip opening a menu of every Collection, plus "New Collection…". */
@Composable
private fun CollectionPicker(
    current: Collection?,
    collections: List<Collection>,
    onPick: (Long?) -> Unit,
    onNew: (Collection) -> Unit,
    sections: SectionChoice,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }

    Box(modifier) {
        Pill(onClick = { expanded = true }) {
            if (current != null) ColorDot(current.color, size = 10.dp)
            Text(
                current?.name ?: stringResource(R.string.to_sort),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text("▾", style = MaterialTheme.typography.labelLarge)
        }
        CollectionMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            collections = collections,
            onPick = onPick,
            onNew = { creating = true },
            offerToSort = true,
        )
    }

    if (creating) {
        CollectionEditorDialog(
            title = stringResource(R.string.new_collection),
            initial = Collection(name = "", color = nextColor(collections.map { it.color })),
            otherNames = collections.map { it.name },
            sections = sections,
            onSave = {
                creating = false
                onNew(it)
            },
            onDismiss = { creating = false },
        )
    }
}

/** The Post's Tags, each with a × removing it at once, then a chip opening the Tag picker. */
@Composable
private fun PostTags(
    tags: List<Tag>,
    postTags: List<Tag>,
    collectionTagIds: Set<Long>,
    onAdd: (Tag) -> Unit,
    onRemove: (Tag) -> Unit,
    onNew: (Tag) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        postTags.forEach { TagChip(it, onRemove = { onRemove(it) }) }
        Pill(onClick = { picking = true }, dashed = true) {
            Text(stringResource(R.string.add_tag), style = MaterialTheme.typography.labelLarge)
        }
    }

    if (picking) {
        TagPickerDialog(
            title = stringResource(R.string.tags),
            tags = tags,
            onPost = postTags,
            inCollection = collectionTagIds,
            onAdd = onAdd,
            onRemove = onRemove,
            onNew = onNew,
            onDismiss = { picking = false },
        )
    }
}

@Preview
@Composable
private fun PostDetailScreenPreview() {
    InstaSavedTheme {
        PostDetailScreen(
            Post(
                shortcode = "C1a2B3c4D5e",
                url = "https://www.instagram.com/p/C1a2B3c4D5e/",
                addedAt = 0,
                title = "Best pasta in town",
                description = "Best pasta in town. Recipe below!",
                collectionId = 1,
                ownerName = "Pasta Grannies",
            ),
            collections = listOf(Collection(1, "🍝 Pasta", PALETTE[0])),
            onBack = {},
            onTextChange = {},
            onCollectionChange = {},
            onNewCollection = {},
            sections = SectionChoice(),
            tags = listOf(Tag(1, "Quick", PALETTE[1]), Tag(2, "Vegan", PALETTE[3])),
            postTags = listOf(Tag(2, "Vegan", PALETTE[3])),
            onAddTag = {},
            onRemoveTag = {},
            onNewTag = {},
            onOpenInInstagram = {},
            onDelete = {},
            showEmbed = false,
        )
    }
}

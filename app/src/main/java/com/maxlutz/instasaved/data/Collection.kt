package com.maxlutz.instasaved.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-named group of Posts. Names are unique, ignoring case: Sync matches Instagram Collections to them by name
 * (ADR-0006).
 *
 * @property color an ARGB color, normally from [PALETTE].
 * @property sectionId the Section the Collection is in, if any. A deleted Section leaves it null (ADR-0014).
 */
@Entity(
    tableName = "collections",
    indices = [Index(value = ["name"], unique = true), Index("sectionId")],
    foreignKeys = [ForeignKey(Section::class, ["id"], ["sectionId"], onDelete = ForeignKey.SET_NULL)],
)
data class Collection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val color: Int,
    val note: String = "",
    val sectionId: Long? = null,
)

/** Whether two Collection names, or two Tag names, are the same name. */
fun sameName(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

data class CollectionWithCount(
    @Embedded val collection: Collection,
    /** Posts in the Collection, Recently deleted ones excluded. */
    val postCount: Int,
)

/**
 * A Collection deleted together with its Posts (ADR-0010): what it was, so that restoring those Posts from Recently
 * deleted can recreate it (ADR-0012). The Posts point here through [Post.deletionId].
 *
 * @property collectionSectionId the Section it was in, if any: the recreated Collection goes back in it if that
 *   Section is still there. Not a reference, so that undoing the Section's deletion finds it again.
 */
@Entity(tableName = "collection_deletions")
data class CollectionDeletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionName: String,
    val collectionColor: Int,
    val collectionNote: String,
    val collectionSectionId: Long? = null,
)

/**
 * A move of all the Posts of a Collection to another, as [CollectionDao.undoMoveAllPosts] needs it.
 *
 * @property posts the Posts as they were before the move. When the move deleted their Collection, its Recently
 *   deleted Posts are among them: they lost it too.
 * @property deleted the Collection the move deleted, if it did.
 */
data class AllPostsMove(val posts: List<Post>, val deleted: Collection?) {
    /** How many Posts changed Collection. */
    val movedCount get() = posts.count { it.deletedAt == null }
}

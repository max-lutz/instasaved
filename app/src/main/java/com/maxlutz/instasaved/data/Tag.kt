package com.maxlutz.instasaved.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** The most Tags a Post can carry. */
const val MAX_TAGS_PER_POST = 4

/**
 * A reusable label for Posts, independent of their Collection. Names are unique, ignoring case.
 *
 * @property color an ARGB color, normally from [PALETTE].
 */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val color: Int,
)

data class TagWithCount(
    @Embedded val tag: Tag,
    /** Posts carrying the Tag, Recently deleted ones excluded. */
    val postCount: Int,
)

/** A Tag on a Post. Rows go with their Post or Tag; a Recently deleted Post keeps its Tags (ADR-0012). */
@Entity(
    tableName = "post_tags",
    primaryKeys = ["postId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(Post::class, ["id"], ["postId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Tag::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PostTag(val postId: Long, val tagId: Long)

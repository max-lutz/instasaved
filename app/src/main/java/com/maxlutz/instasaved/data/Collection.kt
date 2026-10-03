package com.maxlutz.instasaved.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-named group of Posts. Names are unique, ignoring case: Sync matches Instagram Collections to them by name
 * (ADR-0006).
 *
 * @property color an ARGB color, normally from [PALETTE].
 */
@Entity(tableName = "collections", indices = [Index(value = ["name"], unique = true)])
data class Collection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val color: Int,
    val note: String = "",
)

/** Whether two Collection names are the same name. */
fun sameCollectionName(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

data class CollectionWithCount(
    @Embedded val collection: Collection,
    /** Posts in the Collection, Recently deleted ones excluded. */
    val postCount: Int,
)

/**
 * A Collection deleted together with its Posts (ADR-0010): what it was, so that restoring those Posts from Recently
 * deleted can recreate it (ADR-0012). The Posts point here through [Post.deletionId].
 */
@Entity(tableName = "collection_deletions")
data class CollectionDeletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionName: String,
    val collectionColor: Int,
    val collectionNote: String,
)

package com.maxlutz.instasaved.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * A user-named group of Collections on the Saved screen (ADR-0014). Names are unique among Sections, ignoring
 * case. It may be empty, and stays until it is deleted.
 *
 * @property collapsed whether the Saved screen hides its Collections. Not part of the manual backup file.
 */
@Entity(tableName = "sections", indices = [Index(value = ["name"], unique = true)])
data class Section(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val collapsed: Boolean = false,
)

/**
 * The deletion of a Section, as [SectionDao.undoDelete] needs it.
 *
 * @property collectionIds the Collections that were in it.
 */
data class SectionDeletion(val section: Section, val collectionIds: List<Long>)

@Dao
abstract class SectionDao {
    /** All Sections, alphabetically. */
    @Query("SELECT * FROM sections ORDER BY name, id")
    abstract fun observeAll(): Flow<List<Section>>

    @Query("SELECT * FROM sections WHERE id = :id")
    abstract suspend fun get(id: Long): Section?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(section: Section): Long

    /** Creates a Section, expanded. Returns its id, or null if [name] is blank or another Section already has it. */
    open suspend fun create(name: String): Long? {
        if (name.isBlank()) return null
        return insert(Section(name = name.trim())).takeIf { it != -1L }
    }

    @Query("UPDATE OR IGNORE sections SET name = :name WHERE id = :id")
    protected abstract suspend fun renameRow(id: Long, name: String): Int

    /** Renames the Section. Returns false, changing nothing, if [name] is blank or another Section already has it. */
    open suspend fun rename(id: Long, name: String): Boolean = name.isNotBlank() && renameRow(id, name.trim()) == 1

    @Query("UPDATE sections SET collapsed = :collapsed WHERE id = :id")
    abstract suspend fun setCollapsed(id: Long, collapsed: Boolean)

    @Query("SELECT id FROM collections WHERE sectionId = :id ORDER BY id")
    protected abstract suspend fun collectionIdsIn(id: Long): List<Long>

    @Query("DELETE FROM sections WHERE id = :id")
    protected abstract suspend fun deleteRow(id: Long)

    /**
     * Deletes the Section. Its Collections lose their Section through the `ON DELETE SET NULL` reference, and
     * nothing else is deleted. Returns what [undoDelete] needs, or null if the Section is gone already.
     */
    @Transaction
    open suspend fun delete(id: Long): SectionDeletion? {
        val section = get(id) ?: return null
        val collectionIds = collectionIdsIn(id)
        deleteRow(id)
        return SectionDeletion(section, collectionIds)
    }

    // The name column compares ignoring case.
    @Query("SELECT id FROM sections WHERE name = :name")
    protected abstract suspend fun sectionNamed(name: String): Long?

    // A Collection put in another Section meanwhile stays there.
    @Query("UPDATE collections SET sectionId = :sectionId WHERE id IN (:collectionIds) AND sectionId IS NULL")
    protected abstract suspend fun putBack(collectionIds: List<Long>, sectionId: Long)

    /**
     * Undoes [delete]: the Section is back as it was, with its Collections in it, unless a Section has that name
     * by now, which the Collections join instead.
     */
    @Transaction
    open suspend fun undoDelete(deletion: SectionDeletion) {
        val id = sectionNamed(deletion.section.name) ?: insert(deletion.section)
        putBack(deletion.collectionIds, id)
    }
}

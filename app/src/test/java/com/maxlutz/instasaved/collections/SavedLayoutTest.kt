package com.maxlutz.instasaved.collections

import com.maxlutz.instasaved.data.Collection
import com.maxlutz.instasaved.data.CollectionWithCount
import com.maxlutz.instasaved.data.PALETTE
import com.maxlutz.instasaved.data.Section
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedLayoutTest {
    private val food = Section(1, "Food")
    private val travel = Section(2, "Travel")

    private var nextId = 1L

    private fun collection(name: String, section: Section? = null) =
        CollectionWithCount(Collection(nextId++, name, PALETTE[0], sectionId = section?.id), postCount = 0)

    private fun SavedLayout.names() = withoutSection.map { it.collection.name }

    private fun SavedLayout.namesBySection() =
        sections.map { it.section.name to it.collections.map { c -> c.collection.name } }

    @Test
    fun withNoSectionEveryCollectionIsShownAsBefore() {
        val collections = listOf(collection("Japan"), collection("Pasta"))

        val layout = arrangeSaved(collections, sections = emptyList())

        assertEquals(collections, layout.withoutSection)
        assertEquals(emptyList<SectionWithCollections>(), layout.sections)
    }

    @Test
    fun collectionsWithNoSectionComeFirstThenEachSectionWithItsOwn() {
        val collections = listOf(
            collection("Japan", travel),
            collection("Later"),
            collection("Pasta", food),
            collection("Rome", travel),
            collection("Salads", food),
            collection("Wishlist"),
        )

        val layout = arrangeSaved(collections, listOf(food, travel))

        assertEquals(listOf("Later", "Wishlist"), layout.names())
        assertEquals(
            listOf("Food" to listOf("Pasta", "Salads"), "Travel" to listOf("Japan", "Rome")),
            layout.namesBySection(),
        )
    }

    @Test
    fun anEmptySectionIsStillShown() {
        val layout = arrangeSaved(listOf(collection("Pasta", food)), listOf(food, travel))

        assertEquals(listOf("Food" to listOf("Pasta"), "Travel" to emptyList()), layout.namesBySection())
    }

    @Test
    fun aCollapsedSectionKeepsItsCollectionsToCount() {
        val collapsed = food.copy(collapsed = true)

        val layout = arrangeSaved(listOf(collection("Pasta", food), collection("Salads", food)), listOf(collapsed))

        assertEquals(collapsed, layout.sections.single().section)
        assertEquals(2, layout.sections.single().collections.size)
    }

    @Test
    fun everyCollectionIsShownOnce() {
        val collections = listOf(collection("Japan", travel), collection("Later"), collection("Pasta", food))

        val layout = arrangeSaved(collections, listOf(food, travel))

        assertEquals(collections.toSet(), (layout.withoutSection + layout.sections.flatMap { it.collections }).toSet())
    }

    @Test
    fun aCollectionWhoseSectionIsNotThereCountsAsHavingNone() {
        val layout = arrangeSaved(listOf(collection("Japan", travel), collection("Pasta", food)), listOf(food))

        assertEquals(listOf("Japan"), layout.names())
        assertEquals(listOf("Food" to listOf("Pasta")), layout.namesBySection())
    }
}

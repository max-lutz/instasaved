package com.maxlutz.instasaved.collections

import com.maxlutz.instasaved.data.CollectionWithCount
import com.maxlutz.instasaved.data.Section

/** A Section as the Saved screen shows it, with the Collections in it. */
data class SectionWithCollections(val section: Section, val collections: List<CollectionWithCount>)

/**
 * What the Saved screen shows after "All posts": the Collections with no Section, under no header, then every
 * Section, each with its Collections. A Section with no Collection is still there.
 */
data class SavedLayout(val withoutSection: List<CollectionWithCount>, val sections: List<SectionWithCollections>)

/**
 * Lays out [collections] and [sections], both alphabetical, for the Saved screen. Each list keeps its order. A
 * Collection whose Section is not among [sections] counts as having none.
 */
fun arrangeSaved(collections: List<CollectionWithCount>, sections: List<Section>): SavedLayout {
    val sectionIds = sections.mapTo(HashSet()) { it.id }
    val bySection = collections.groupBy { it.collection.sectionId?.takeIf { id -> id in sectionIds } }
    return SavedLayout(
        withoutSection = bySection[null].orEmpty(),
        sections = sections.map { SectionWithCollections(it, bySection[it.id].orEmpty()) },
    )
}

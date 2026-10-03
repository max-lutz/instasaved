package com.maxlutz.instasaved.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteTest {
    @Test
    fun paletteHasTwelveDistinctOpaqueColors() {
        assertEquals(12, PALETTE.toSet().size)
        assertEquals(0xFFB5533C.toInt(), PALETTE.first())
    }

    @Test
    fun theFirstCollectionGetsTheFirstColor() = assertEquals(PALETTE[0], nextColor(emptyList()))

    @Test
    fun newCollectionsWalkThePaletteInOrder() {
        val used = mutableListOf<Int>()
        repeat(14) { used += nextColor(used) }

        assertEquals(PALETTE + PALETTE.take(2), used)
    }

    @Test
    fun aColorFreedByADeletionComesBackFirst() =
        assertEquals(PALETTE[1], nextColor(PALETTE.take(5) - PALETTE[1]))

    @Test
    fun recoloringCountsTowardsTheColorItWasChangedTo() =
        assertEquals(PALETTE[2], nextColor(listOf(PALETTE[0], PALETTE[0], PALETTE[1])))

    @Test
    fun aColorOutsideThePaletteIsIgnored() = assertEquals(PALETTE[0], nextColor(listOf(0xFF000000.toInt())))
}

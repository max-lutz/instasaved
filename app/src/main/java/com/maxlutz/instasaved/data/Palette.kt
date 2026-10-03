package com.maxlutz.instasaved.data

/** The 12 colors Collections and Tags pick from, as ARGB, in Socials Organizer's order. */
val PALETTE: List<Int> = listOf(
    0xFFB5533C, 0xFF4C6B8A, 0xFF6B7A4F, 0xFFB8863B, 0xFF7A5670, 0xFF3D6E8F,
    0xFF9C5B8C, 0xFF5E7A9A, 0xFFA6763C, 0xFF4A7C6B, 0xFF8C6B47, 0xFF6E5B8C,
).map { it.toInt() }

/**
 * The color a new Collection (or Tag) gets, given the colors Collections (or Tags) already [used]: the least-used
 * palette color, earliest in the palette on a tie. Creating one after another walks the palette in order; a color freed
 * by a deletion comes back first.
 */
fun nextColor(used: List<Int>): Int = PALETTE.minBy { color -> used.count { it == color } }

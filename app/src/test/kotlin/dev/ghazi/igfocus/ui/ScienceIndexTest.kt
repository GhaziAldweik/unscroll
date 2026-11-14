package dev.ghazi.igfocus.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScienceIndexTest {

    @Test
    fun `a new note each day, cycling through all of them`() {
        val seen = (0L until 17L).map { scienceIndex(20_000 + it, 17) }
        assertEquals((0 until 17).toSet(), seen.toSet())
        assertNotEquals(scienceIndex(20_000, 17), scienceIndex(20_001, 17))
        assertEquals(scienceIndex(20_000, 17), scienceIndex(20_017, 17))
    }

    @Test
    fun `days before 1970 still give a valid note`() {
        assertEquals(16, scienceIndex(-1, 17))
    }
}

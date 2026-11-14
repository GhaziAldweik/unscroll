package dev.ghazi.igfocus.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class StepInsTest {

    @Test
    fun `counts add up per day`() {
        val s = StepIns.EMPTY.plusOne(100).plusOne(100).plusOne(101)
        assertEquals(2, s.on(100))
        assertEquals(1, s.on(101))
        assertEquals(0, s.on(99))
    }

    @Test
    fun `the week is the seven days ending today, oldest first`() {
        val s = StepIns.EMPTY.plusOne(94).plusOne(95).plusOne(101).plusOne(101)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 2), s.week(101))
        assertEquals(3, s.weekTotal(101))
    }

    @Test
    fun `days older than two weeks are dropped`() {
        val s = StepIns.EMPTY.plusOne(80).plusOne(100)
        assertEquals(0, s.on(80))
        assertEquals("100:1", s.encode())
    }

    @Test
    fun `encode and decode round-trip`() {
        val s = StepIns.EMPTY.plusOne(100).plusOne(100).plusOne(103)
        assertEquals(s.encode(), StepIns.decode(s.encode()).encode())
        assertEquals("100:2,103:1", s.encode())
    }

    @Test
    fun `bad stored values are skipped, not fatal`() {
        val s = StepIns.decode("100:2,garbage,101:x,102:-1, 103 : 4 ")
        assertEquals(2, s.on(100))
        assertEquals(0, s.on(101))
        assertEquals(0, s.on(102))
        assertEquals(4, s.on(103))
        assertEquals(StepIns.EMPTY.encode(), StepIns.decode(null).encode())
    }
}

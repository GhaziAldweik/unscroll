package dev.ghazi.igfocus.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureHistoryTest {

    private fun sig(vararg ids: String) = ScreenSignature(ids.toList())

    @Test
    fun `a new signature is reported as new and a repeat is not`() {
        val history = SignatureHistory()
        assertTrue(history.add(sig("a")))
        assertFalse(history.add(sig("a")))
        assertEquals(1, history.size)
    }

    @Test
    fun `a repeat moves to the newest position`() {
        val history = SignatureHistory()
        history.add(sig("a"))
        history.add(sig("b"))
        history.add(sig("a"))
        assertEquals(listOf(sig("b"), sig("a")), history.snapshot())
    }

    @Test
    fun `the oldest signature is dropped at capacity`() {
        val history = SignatureHistory(capacity = 2)
        history.add(sig("a"))
        history.add(sig("b"))
        history.add(sig("c"))
        assertEquals(listOf(sig("b"), sig("c")), history.snapshot())
    }
}

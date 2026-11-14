package dev.ghazi.igfocus.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoriesRuleTest {

    private val hidden = setOf("noisy_friend")

    private fun show(author: String, storiesOn: Boolean, hidden: Set<String> = this.hidden) =
        StoriesRule.shouldShow(author, storiesOn, hidden, ownUsername = "me_example")

    @Test
    fun `stories on shows people who are not hidden`() {
        assertTrue(show("close_friend", storiesOn = true))
    }

    @Test
    fun `stories on still hides a hidden person`() {
        assertFalse(show("noisy_friend", storiesOn = true))
    }

    @Test
    fun `stories off hides everyone else`() {
        assertFalse(show("close_friend", storiesOn = false))
        assertFalse(show("noisy_friend", storiesOn = false))
    }

    /** The example in the brief: off, then on again, and the hidden person is still hidden. */
    @Test
    fun `turning stories off and on again keeps hidden people hidden`() {
        assertFalse(show("noisy_friend", storiesOn = true))
        assertFalse(show("noisy_friend", storiesOn = false))
        assertFalse(show("noisy_friend", storiesOn = true))
        assertTrue(show("close_friend", storiesOn = true))
    }

    @Test
    fun `own stories always show`() {
        assertTrue(show("me_example", storiesOn = false))
        assertTrue(show("me_example", storiesOn = true, hidden = setOf("me_example")))
    }

    @Test
    fun `usernames match regardless of case, spaces or a leading at sign`() {
        assertFalse(show(" @Noisy_Friend ", storiesOn = true))
        assertTrue(show("@ME_EXAMPLE", storiesOn = false))
        assertEquals("noisy_friend", StoriesRule.normalize("@ Noisy_Friend"))
    }

    @Test
    fun `an unreadable author is not treated as the owner`() {
        assertFalse(StoriesRule.shouldShow("", storiesOn = false, hidden = emptySet(), ownUsername = ""))
        assertTrue(StoriesRule.shouldShow("", storiesOn = true, hidden = emptySet(), ownUsername = ""))
    }
}

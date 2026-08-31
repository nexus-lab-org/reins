package co.maxasif.reins.presentation.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionSwitchingTest {
    @Test
    fun `swipe left cycles to the next session`() {
        val ids = listOf("a", "b", "c")
        assertEquals("b", cycleSession(ids, currentSessionId = "a", direction = 1))
        assertEquals("c", cycleSession(ids, currentSessionId = "b", direction = 1))
    }

    @Test
    fun `swipe left wraps from the last session back to the first`() {
        val ids = listOf("a", "b", "c")
        assertEquals("a", cycleSession(ids, currentSessionId = "c", direction = 1))
    }

    @Test
    fun `swipe right cycles to the previous session`() {
        val ids = listOf("a", "b", "c")
        assertEquals("a", cycleSession(ids, currentSessionId = "b", direction = -1))
    }

    @Test
    fun `swipe right wraps from the first session back to the last`() {
        val ids = listOf("a", "b", "c")
        assertEquals("c", cycleSession(ids, currentSessionId = "a", direction = -1))
    }

    @Test
    fun `a single session never cycles away from itself`() {
        assertEquals("a", cycleSession(listOf("a"), currentSessionId = "a", direction = 1))
        assertEquals("a", cycleSession(listOf("a"), currentSessionId = "a", direction = -1))
    }

    @Test
    fun `no sessions returns the current id unchanged`() {
        assertEquals("a", cycleSession(emptyList(), currentSessionId = "a", direction = 1))
    }

    @Test
    fun `an unknown current session id returns unchanged rather than guessing`() {
        assertEquals("z", cycleSession(listOf("a", "b"), currentSessionId = "z", direction = 1))
    }
}

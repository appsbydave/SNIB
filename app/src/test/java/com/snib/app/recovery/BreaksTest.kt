package com.snib.app.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreaksTest {

    @Test fun `week key has the ISO shape`() {
        assertTrue(Breaks.weekKey().matches(Regex("""\d{4}-W\d{2}""")))
    }

    @Test fun `week key is stable for the same instant`() {
        val t = 1_767_225_600_000L // a fixed instant
        assertEquals(Breaks.weekKey(t), Breaks.weekKey(t))
    }

    @Test fun `cost sentence counts down in plain words`() {
        assertEquals("This uses one of 3. Two left until Monday.", Breaks.costSentence(3))
        assertEquals("This uses one of 3. One left until Monday.", Breaks.costSentence(2))
        assertEquals("This uses one of 3. None left until Monday.", Breaks.costSentence(1))
    }
}

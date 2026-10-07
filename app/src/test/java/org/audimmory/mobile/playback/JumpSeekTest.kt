package org.audimmory.mobile.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the relative-seek arithmetic behind Bluetooth / notification
 * previous-next, which used to restart the book instead of jumping.
 */
class JumpSeekTest {
    private val tenMinutes = 600_000L

    @Test
    fun jumpsBackwardMidBook() {
        assertEquals(285_000L, JumpSeek.target(currentMs = 300_000, deltaMs = -15_000, durationMs = tenMinutes))
    }

    @Test
    fun jumpsForwardMidBook() {
        assertEquals(330_000L, JumpSeek.target(currentMs = 300_000, deltaMs = 30_000, durationMs = tenMinutes))
    }

    @Test
    fun clampsToStartInsteadOfSeekingNegative() {
        assertEquals(0L, JumpSeek.target(currentMs = 5_000, deltaMs = -15_000, durationMs = tenMinutes))
        assertEquals(0L, JumpSeek.target(currentMs = 0, deltaMs = -15_000, durationMs = tenMinutes))
    }

    @Test
    fun clampsToDurationInsteadOfSeekingPastTheEnd() {
        assertEquals(tenMinutes, JumpSeek.target(currentMs = 595_000, deltaMs = 30_000, durationMs = tenMinutes))
        assertEquals(tenMinutes, JumpSeek.target(currentMs = tenMinutes, deltaMs = 30_000, durationMs = tenMinutes))
    }

    @Test
    fun landsExactlyOnTheBoundaries() {
        assertEquals(0L, JumpSeek.target(currentMs = 15_000, deltaMs = -15_000, durationMs = tenMinutes))
        assertEquals(tenMinutes, JumpSeek.target(currentMs = 570_000, deltaMs = 30_000, durationMs = tenMinutes))
    }

    /**
     * Media3 reports `C.TIME_UNSET` before the timeline is ready. Clamping to it
     * would pin playback to the start of the book, so only the floor applies.
     */
    @Test
    fun ignoresAnUnknownDurationAsAnUpperBound() {
        val timeUnset = Long.MIN_VALUE + 1
        assertEquals(330_000L, JumpSeek.target(currentMs = 300_000, deltaMs = 30_000, durationMs = timeUnset))
        assertEquals(330_000L, JumpSeek.target(currentMs = 300_000, deltaMs = 30_000, durationMs = 0))
        assertEquals(285_000L, JumpSeek.target(currentMs = 300_000, deltaMs = -15_000, durationMs = timeUnset))
    }

    @Test
    fun treatsAnUnsetCurrentPositionAsTheStart() {
        assertEquals(30_000L, JumpSeek.target(currentMs = -1, deltaMs = 30_000, durationMs = tenMinutes))
        assertEquals(0L, JumpSeek.target(currentMs = -1, deltaMs = -15_000, durationMs = tenMinutes))
    }

    @Test
    fun doesNotOverflowIntoANegativePosition() {
        assertEquals(
            tenMinutes,
            JumpSeek.target(currentMs = Long.MAX_VALUE, deltaMs = 30_000, durationMs = tenMinutes),
        )
        assertEquals(
            Long.MAX_VALUE,
            JumpSeek.target(currentMs = Long.MAX_VALUE, deltaMs = 30_000, durationMs = 0),
        )
    }
}

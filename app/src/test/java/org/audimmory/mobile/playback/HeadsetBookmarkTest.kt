package org.audimmory.mobile.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the next-track-button bookmark debounce. Nothing downstream
 * deduplicates, so every press that gets through becomes a row.
 */
class HeadsetBookmarkTest {
    @Test
    fun createsTheFirstBookmarkOfTheSession() {
        assertTrue(HeadsetBookmark.shouldCreate(nowMs = 0, lastCreatedAtMs = null))
        assertTrue(HeadsetBookmark.shouldCreate(nowMs = 9_999_999, lastCreatedAtMs = null))
    }

    @Test
    fun swallowsADoubleFiredPress() {
        assertFalse(HeadsetBookmark.shouldCreate(nowMs = 10_050, lastCreatedAtMs = 10_000))
    }

    @Test
    fun swallowsAHeldButtonForTheWholeWindow() {
        val pressedAt = 10_000L
        for (elapsed in longArrayOf(0, 100, 500, 1_000, 1_499)) {
            assertFalse(
                "press $elapsed ms later should be swallowed",
                HeadsetBookmark.shouldCreate(nowMs = pressedAt + elapsed, lastCreatedAtMs = pressedAt),
            )
        }
    }

    @Test
    fun allowsAPressOnceTheWindowHasPassed() {
        assertTrue(HeadsetBookmark.shouldCreate(nowMs = 11_500, lastCreatedAtMs = 10_000))
        assertTrue(HeadsetBookmark.shouldCreate(nowMs = 60_000, lastCreatedAtMs = 10_000))
    }

    @Test
    fun treatsTheBoundaryAsAllowed() {
        val boundary = 10_000L + HeadsetBookmark.MIN_INTERVAL_MS
        assertFalse(HeadsetBookmark.shouldCreate(nowMs = boundary - 1, lastCreatedAtMs = 10_000))
        assertTrue(HeadsetBookmark.shouldCreate(nowMs = boundary, lastCreatedAtMs = 10_000))
    }

    @Test
    fun honoursACustomInterval() {
        assertFalse(HeadsetBookmark.shouldCreate(10_100, 10_000, minIntervalMs = 200))
        assertTrue(HeadsetBookmark.shouldCreate(10_300, 10_000, minIntervalMs = 200))
    }

    /**
     * elapsedRealtime() only ever moves forward, but a zero interval must not
     * become an accidental "always deny".
     */
    @Test
    fun allowsEveryPressWhenTheIntervalIsZero() {
        assertTrue(HeadsetBookmark.shouldCreate(10_000, 10_000, minIntervalMs = 0))
    }
}

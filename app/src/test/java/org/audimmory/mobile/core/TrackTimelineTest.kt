package org.audimmory.mobile.core

import org.audimmory.mobile.core.TrackTimeline.Track
import org.audimmory.mobile.core.TrackTimeline.TrackPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackTimelineTest {
    // Mirrors the layout Grimmory reports for a three-file mp3 folder.
    private val tracks =
        listOf(
            Track(index = 0, startMs = 0, durationMs = 25_051),
            Track(index = 1, startMs = 25_051, durationMs = 30_040),
            Track(index = 2, startMs = 55_091, durationMs = 35_030),
        )

    @Test
    fun `absolute position maps into the containing track`() {
        assertEquals(TrackPosition(0, 10_000), TrackTimeline.toTrackPosition(tracks, 10_000))
        assertEquals(TrackPosition(1, 4_949), TrackTimeline.toTrackPosition(tracks, 30_000))
        assertEquals(TrackPosition(2, 1_000), TrackTimeline.toTrackPosition(tracks, 56_091))
    }

    @Test
    fun `a boundary belongs to the later track`() {
        assertEquals(TrackPosition(1, 0), TrackTimeline.toTrackPosition(tracks, 25_051))
    }

    @Test
    fun `positions outside the book clamp`() {
        assertEquals(TrackPosition(0, 0), TrackTimeline.toTrackPosition(tracks, -5))
        assertEquals(TrackPosition(2, 35_030), TrackTimeline.toTrackPosition(tracks, 999_999))
    }

    @Test
    fun `round trip preserves the position`() {
        for (ms in listOf(0L, 1L, 25_050L, 25_051L, 55_090L, 70_000L)) {
            val p = TrackTimeline.toTrackPosition(tracks, ms)
            assertEquals(ms, TrackTimeline.toAbsoluteMs(tracks, p.trackIndex, p.offsetMs))
        }
    }

    @Test
    fun `null or unknown track index is an absolute position`() {
        assertEquals(42_000, TrackTimeline.toAbsoluteMs(tracks, null, 42_000))
        assertEquals(42_000, TrackTimeline.toAbsoluteMs(tracks, 9, 42_000))
    }

    @Test
    fun `single track and empty layouts are identity`() {
        val single = listOf(Track(0, 0, 120_000))
        assertEquals(TrackPosition(0, 61_000), TrackTimeline.toTrackPosition(single, 61_000))
        assertEquals(TrackPosition(0, 5), TrackTimeline.toTrackPosition(emptyList(), 5))
    }

    @Test
    fun `total duration is the end of the last track`() {
        assertEquals(90_121, TrackTimeline.totalDurationMs(tracks))
        assertEquals(0, TrackTimeline.totalDurationMs(emptyList()))
    }
}

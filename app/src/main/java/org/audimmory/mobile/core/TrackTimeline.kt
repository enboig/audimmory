package org.audimmory.mobile.core

/**
 * Maps between a whole-book position and Grimmory's per-track positions.
 *
 * Grimmory serves a folder-based audiobook (typically a directory of `.mp3`
 * files) as separate tracks, and stores progress and bookmarks for such books
 * as `trackIndex` plus `positionMs` *within that track*. The app plays every
 * book as one continuous timeline, so positions are converted at the API
 * boundary. Single-file books have one track and the conversion is identity.
 */
object TrackTimeline {
    /** One audio track, positioned on the book's timeline. */
    data class Track(
        val index: Int,
        val startMs: Long,
        val durationMs: Long,
    )

    /** A position inside a specific track. */
    data class TrackPosition(
        val trackIndex: Int,
        val offsetMs: Long,
    )

    /**
     * Converts an absolute book position to the track containing it and the
     * offset inside that track. Positions past the end clamp to the last track;
     * a position exactly on a boundary belongs to the later track.
     */
    fun toTrackPosition(
        tracks: List<Track>,
        absoluteMs: Long,
    ): TrackPosition {
        val position = absoluteMs.coerceAtLeast(0)
        val sorted = tracks.sortedBy { it.index }
        if (sorted.isEmpty()) return TrackPosition(0, position)

        val containing = sorted.lastOrNull { it.startMs <= position } ?: sorted.first()
        val offset = (position - containing.startMs).coerceAtLeast(0)
        val clamped = if (containing.durationMs > 0) offset.coerceAtMost(containing.durationMs) else offset
        return TrackPosition(containing.index, clamped)
    }

    /**
     * Converts a per-track position back to an absolute book position. An
     * unknown or null track index is treated as an absolute position, which is
     * how Grimmory stores single-file books.
     */
    fun toAbsoluteMs(
        tracks: List<Track>,
        trackIndex: Int?,
        offsetMs: Long,
    ): Long {
        val offset = offsetMs.coerceAtLeast(0)
        if (trackIndex == null) return offset
        val track = tracks.firstOrNull { it.index == trackIndex } ?: return offset
        return track.startMs + offset
    }

    /** Total book duration in milliseconds. */
    fun totalDurationMs(tracks: List<Track>): Long = tracks.maxOfOrNull { it.startMs + it.durationMs } ?: 0
}

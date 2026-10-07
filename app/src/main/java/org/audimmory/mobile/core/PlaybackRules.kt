package org.audimmory.mobile.core

/**
 * Pure, layer-agnostic playback rules.
 *
 * Grimmory marks a book read only at 99.5%, which in a long audiobook can be
 * minutes of closing credits. The app keeps its own, earlier threshold and,
 * when a book crosses it, reports 100% to Grimmory
 * ([org.audimmory.mobile.data.remote.GrimmoryClient.updateProgress]) so both
 * sides agree the book is finished.
 */
object PlaybackRules {
    /** Fraction of a book's duration at which it is considered finished. */
    const val FINISHED_THRESHOLD: Double = 0.98

    /**
     * Returns true when [currentSeconds] counts as "finished" for a book of the
     * given [durationSeconds] (at or past [FINISHED_THRESHOLD] of the way through).
     */
    fun finishedAtPosition(
        currentSeconds: Double,
        durationSeconds: Double,
    ): Boolean = durationSeconds > 0 && currentSeconds / durationSeconds >= FINISHED_THRESHOLD
}

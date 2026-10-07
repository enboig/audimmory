package org.audimmory.mobile.playback

/**
 * Relative-seek target arithmetic for the audiobook transport, kept pure so it
 * can be unit-tested on the JVM. Deliberately free of Android and Media3
 * imports.
 */
object JumpSeek {
    /**
     * Returns the absolute position to seek to when jumping [deltaMs] from
     * [currentMs], clamped so the player never seeks outside the media.
     *
     * @param currentMs Current playback position in milliseconds. Values below
     *   zero (an unset position) are treated as the start of the media.
     * @param deltaMs Signed jump amount in milliseconds; negative jumps back.
     * @param durationMs Media duration in milliseconds, or a non-positive value
     *   when the duration is not yet known (Media3 reports `C.TIME_UNSET`).
     *   With an unknown duration only the lower bound is enforced, because
     *   clamping to a bogus upper bound would pin playback to the start.
     */
    fun target(
        currentMs: Long,
        deltaMs: Long,
        durationMs: Long,
    ): Long {
        val from = currentMs.coerceAtLeast(0)
        val raw =
            // Guard the addition itself: a huge delta plus a huge position
            // would otherwise wrap around to a negative position.
            if (deltaMs > 0 && from > Long.MAX_VALUE - deltaMs) {
                Long.MAX_VALUE
            } else {
                from + deltaMs
            }
        val floored = raw.coerceAtLeast(0)
        return if (durationMs > 0) floored.coerceAtMost(durationMs) else floored
    }
}

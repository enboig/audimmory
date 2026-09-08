package live.pageless.mobile.playback

/**
 * Debounce for bookmarks created from the next-track button, kept pure so it can
 * be unit-tested on the JVM. Deliberately free of Android imports.
 *
 * Nothing downstream deduplicates: `BookmarkRepository.add` mints a fresh UUID on
 * every call and `bookmarks` has no unique index on `(bookId, positionSeconds)`,
 * so an unfiltered burst of key events becomes a burst of identical rows. Two
 * bursts are realistic — a headset that double-fires a press, and auto-repeat
 * from a button held down.
 */
object HeadsetBookmark {
    /**
     * Presses closer together than this are treated as one. Long enough to
     * absorb a double-fire or a held button, short enough that deliberately
     * marking two nearby moments still works.
     */
    const val MIN_INTERVAL_MS = 1_500L

    /**
     * Whether a press at [nowMs] should create a bookmark, given the time the
     * last one was created ([lastCreatedAtMs], null if none this session).
     *
     * Both times must come from the same monotonic clock —
     * `SystemClock.elapsedRealtime()`, not wall clock, so a clock adjustment
     * mid-book cannot suppress a press or let a burst through.
     */
    fun shouldCreate(
        nowMs: Long,
        lastCreatedAtMs: Long?,
        minIntervalMs: Long = MIN_INTERVAL_MS,
    ): Boolean {
        if (lastCreatedAtMs == null) return true
        return nowMs - lastCreatedAtMs >= minIntervalMs
    }
}

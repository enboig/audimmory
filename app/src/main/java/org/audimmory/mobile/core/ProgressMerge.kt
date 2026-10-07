package org.audimmory.mobile.core

/**
 * Pure last-write-wins merge for playback progress: the newer `lastPlayedAt`
 * wins. Kept pure so it is unit-testable without Room/network.
 *
 * Timestamps are compared as instants. Plain string comparison is not enough:
 * Grimmory and `Instant.toString()` emit different fractional-second
 * precisions, and `"…:22Z"` sorts after `"…:22.787Z"` as text even though it is
 * earlier. Unparseable timestamps fall back to string comparison.
 */
object ProgressMerge {
    interface Timestamped {
        val lastPlayedAt: String?
    }

    /**
     * Returns true when [incoming] should replace [current] (i.e. it is newer or
     * there is no current record). A null [current] always accepts incoming; a
     * null incoming timestamp never wins over a non-null stored one.
     */
    fun incomingWins(
        current: Timestamped?,
        incoming: Timestamped,
    ): Boolean {
        if (current == null) return true
        val currentTs = current.lastPlayedAt ?: return true
        val incomingTs = incoming.lastPlayedAt ?: return false
        val currentMs = Iso8601.toEpochMillis(currentTs)
        val incomingMs = Iso8601.toEpochMillis(incomingTs)
        return if (currentMs != null && incomingMs != null) incomingMs >= currentMs else incomingTs >= currentTs
    }
}

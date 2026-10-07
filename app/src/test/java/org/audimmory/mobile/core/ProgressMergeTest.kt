package org.audimmory.mobile.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Last-write-wins rule used when merging pulled progress. */
class ProgressMergeTest {
    private class Ts(
        override val lastPlayedAt: String?,
    ) : ProgressMerge.Timestamped

    @Test
    fun incomingWinsWhenNoCurrent() {
        assertTrue(ProgressMerge.incomingWins(null, Ts("2026-01-01T00:00:00Z")))
    }

    @Test
    fun incomingWinsWhenCurrentHasNoTimestamp() {
        assertTrue(ProgressMerge.incomingWins(Ts(null), Ts("2026-01-01T00:00:00Z")))
    }

    @Test
    fun incomingWinsWhenStrictlyNewer() {
        assertTrue(
            ProgressMerge.incomingWins(
                Ts("2026-01-01T00:00:00Z"),
                Ts("2026-01-02T00:00:00Z"),
            ),
        )
    }

    @Test
    fun incomingWinsWhenEqual() {
        // Equal timestamps: incoming (server) wins by >= to converge state.
        assertTrue(
            ProgressMerge.incomingWins(
                Ts("2026-01-01T00:00:00Z"),
                Ts("2026-01-01T00:00:00Z"),
            ),
        )
    }

    @Test
    fun staleIncomingLoses() {
        assertFalse(
            ProgressMerge.incomingWins(
                Ts("2026-01-02T00:00:00Z"),
                Ts("2026-01-01T00:00:00Z"),
            ),
        )
    }

    @Test
    fun incomingWithNoTimestampLosesToStoredValue() {
        assertFalse(ProgressMerge.incomingWins(Ts("2026-01-01T00:00:00Z"), Ts(null)))
    }

    @Test
    fun `timestamps with different fractional precision compare as instants`() {
        // As text "…:22Z" sorts after "…:22.787Z", but it is the earlier instant.
        assertTrue(ProgressMerge.incomingWins(Ts("2026-10-07T17:31:22Z"), Ts("2026-10-07T17:31:22.787Z")))
        assertFalse(ProgressMerge.incomingWins(Ts("2026-10-07T17:31:22.787Z"), Ts("2026-10-07T17:31:22Z")))
    }
}

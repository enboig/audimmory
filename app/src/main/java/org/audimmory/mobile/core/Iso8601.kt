package org.audimmory.mobile.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** UTC ISO-8601 timestamps, the format Grimmory uses for `Instant` fields. */
object Iso8601 {
    fun now(): String = Instant.now().toString()

    /**
     * Parses an ISO-8601 instant to epoch milliseconds, or null if it cannot be
     * parsed. A timestamp without an offset (Grimmory serialises some
     * `LocalDateTime` fields that way) is read as UTC.
     */
    fun toEpochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .getOrNull()
    }
}

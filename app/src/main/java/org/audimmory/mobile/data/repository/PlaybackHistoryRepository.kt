package org.audimmory.mobile.data.repository

import android.os.Build
import kotlinx.coroutines.flow.Flow
import org.audimmory.mobile.core.Iso8601
import org.audimmory.mobile.data.local.PlaybackEventEntity
import org.audimmory.mobile.data.local.PlaybackHistoryDao
import org.audimmory.mobile.data.local.PlaybackSessionEntity
import org.audimmory.mobile.data.remote.GrimmoryClient
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackSessionStart(
    val bookId: String,
    val title: String?,
    val authors: String?,
    val playMethod: String,
    val positionSeconds: Double,
    val durationSeconds: Double,
)

@Singleton
class PlaybackHistoryRepository
    @Inject
    constructor(
        private val dao: PlaybackHistoryDao,
        private val client: GrimmoryClient,
        private val connectionStatusRepository: ConnectionStatusRepository,
    ) {
        fun observeSessionsForBook(bookId: String): Flow<List<PlaybackSessionEntity>> = dao.observeSessionsForBook(bookId)

        fun observeEventsForBook(bookId: String): Flow<List<PlaybackEventEntity>> = dao.observeEventsForBook(bookId)

        suspend fun startSession(input: PlaybackSessionStart): String {
            val now = Iso8601.now()
            val sessionId = UUID.randomUUID().toString()
            dao.upsertSession(
                PlaybackSessionEntity(
                    id = sessionId,
                    bookId = input.bookId,
                    title = input.title,
                    authors = input.authors,
                    playMethod = input.playMethod,
                    deviceInfo = deviceInfo(),
                    startedAt = now,
                    updatedAt = now,
                    endedAt = null,
                    timeListenedSeconds = 0,
                    lastPositionSeconds = input.positionSeconds.coerceAtLeast(0.0),
                    durationSeconds = input.durationSeconds.coerceAtLeast(0.0),
                ),
            )
            recordEvent(sessionId, input.bookId, "Play", input.positionSeconds)
            return sessionId
        }

        suspend fun addListeningTime(
            sessionId: String,
            seconds: Long,
            positionSeconds: Double,
        ) {
            val session = dao.getSession(sessionId) ?: return
            dao.upsertSession(
                session.copy(
                    updatedAt = Iso8601.now(),
                    timeListenedSeconds = session.timeListenedSeconds + seconds.coerceAtLeast(0),
                    lastPositionSeconds = positionSeconds.coerceAtLeast(0.0),
                    dirty = true,
                ),
            )
        }

        suspend fun endSession(
            sessionId: String,
            positionSeconds: Double,
        ) {
            val session = dao.getSession(sessionId) ?: return
            val now = Iso8601.now()
            dao.upsertSession(
                session.copy(
                    updatedAt = now,
                    endedAt = now,
                    lastPositionSeconds = positionSeconds.coerceAtLeast(0.0),
                    dirty = true,
                ),
            )
        }

        suspend fun recordEvent(
            sessionId: String,
            bookId: String,
            event: String,
            positionSeconds: Double,
            type: String = "Playback",
            serverSyncAttempted: Boolean = false,
            serverSyncSuccess: Boolean? = null,
            serverSyncMessage: String? = null,
        ) {
            dao.upsertEvent(
                PlaybackEventEntity(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    bookId = bookId,
                    event = event,
                    type = type,
                    positionSeconds = positionSeconds.coerceAtLeast(0.0),
                    timestamp = Iso8601.now(),
                    serverSyncAttempted = serverSyncAttempted,
                    serverSyncSuccess = serverSyncSuccess,
                    serverSyncMessage = serverSyncMessage,
                ),
            )
        }

        /**
         * Sends listening to Grimmory as reading sessions.
         *
         * Grimmory records a reading session once, as a whole, while a local
         * session stays open across pauses. So each stretch of listening — a
         * "Play" event up to the "Pause" that ended it — is sent as one Grimmory
         * session when its Pause event is synced. A Pause is only ever synced
         * once, so nothing is recorded twice; an open stretch waits for its
         * Pause. The other events and the local session rows have no Grimmory
         * counterpart and stay on the device for the book history screen.
         */
        suspend fun sync(): Result<Unit> {
            val events = dao.dirtyEvents()
            val sessions = dao.dirtySessions()
            if (sessions.isEmpty() && events.isEmpty()) return Result.success(Unit)

            val result =
                runCatching {
                    for (pause in events.filter { it.event == "Pause" }.sortedBy { it.timestamp }) {
                        val session = dao.getSession(pause.sessionId)
                        val play = stretchStart(pause)
                        if (session != null && play != null) {
                            client.recordListeningStretch(session, play, pause)
                        }
                        dao.clearEventDirty(listOf(pause.id))
                    }
                    val others = events.filter { it.event != "Pause" }
                    if (others.isNotEmpty()) dao.clearEventDirty(others.map { it.id })
                    if (sessions.isNotEmpty()) dao.clearSessionDirty(sessions.map { it.id })
                }

            result
                .onSuccess { connectionStatusRepository.markServerSuccess() }
                .onFailure { connectionStatusRepository.markServerFailure() }
            return result
        }

        private fun deviceInfo(): String = "Android ${Build.VERSION.RELEASE}\n${Build.MANUFACTURER} ${Build.MODEL}".trim()

        /** The latest "Play" of the same session before [pause], if any. */
        private suspend fun stretchStart(pause: PlaybackEventEntity): PlaybackEventEntity? {
            val pauseMs = Iso8601.toEpochMillis(pause.timestamp) ?: return null
            return dao
                .eventsForSession(pause.sessionId)
                .filter { it.event == "Play" }
                .mapNotNull { event -> Iso8601.toEpochMillis(event.timestamp)?.let { it to event } }
                .filter { (ms, _) -> ms <= pauseMs }
                .maxByOrNull { (ms, _) -> ms }
                ?.second
        }
    }

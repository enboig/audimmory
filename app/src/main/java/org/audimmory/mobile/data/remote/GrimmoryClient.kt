package org.audimmory.mobile.data.remote

import org.audimmory.mobile.core.Iso8601
import org.audimmory.mobile.core.PlaybackRules
import org.audimmory.mobile.core.TimeFormat
import org.audimmory.mobile.core.TrackTimeline
import org.audimmory.mobile.data.local.BookDao
import org.audimmory.mobile.data.local.PlaybackEventEntity
import org.audimmory.mobile.data.local.PlaybackSessionEntity
import org.audimmory.mobile.data.local.TrackDao
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * Audiobook-shaped view of the Grimmory API.
 *
 * Grimmory is a general book server (EPUB, PDF, comics and audiobooks), so this
 * adapter narrows it to audiobooks and converts its wire format into the app
 * models in `Dtos.kt`. Repositories talk to this class, never to
 * [GrimmoryApi] directly, which keeps the server's quirks in one place:
 *
 * - IDs are numeric on the server and strings in the app.
 * - Folder-based audiobooks store positions per track; the app uses one
 *   continuous timeline (see [TrackTimeline]).
 * - Series, genres, narrators and publishers are plain names rather than
 *   entities, so the name doubles as the facet ID.
 * - Collections map to Grimmory shelves and playlists to magic shelves.
 */
@Singleton
class GrimmoryClient
    @Inject
    constructor(
        private val api: GrimmoryApi,
        private val bookDao: BookDao,
        private val trackDao: TrackDao,
    ) {
        /** Track layouts fetched this process, for books whose detail isn't cached yet. */
        private val layoutCache = ConcurrentHashMap<String, AudioLayout>()

        suspend fun login(
            username: String,
            password: String,
        ): GrimmoryTokens = api.login(GrimmoryLoginRequest(username, password))

        suspend fun logout(refreshToken: String?) = api.logout(GrimmoryLogoutRequest(refreshToken))

        suspend fun me(): AccountDto {
            val user = api.me()
            val version = runCatching { api.version().current }.getOrNull()
            val permissions = user.permissions
            return AccountDto(
                username = user.username,
                displayName = user.name?.takeIf { it.isNotBlank() } ?: user.username,
                canDownload = permissions?.isAdmin == true || permissions?.canDownload == true,
                serverVersion = version,
            )
        }

        suspend fun libraries(): List<LibraryDto> = api.libraries().map { LibraryDto(it.id.toString(), it.name) }

        /** Every audiobook visible to the user, across all pages. */
        suspend fun books(): List<BookSummaryDto> = allPages { page -> api.books(page, GrimmoryApi.PAGE_SIZE) }.map { it.toSummary() }

        /** Book detail merged with its audio layout (tracks, chapters) and progress. */
        suspend fun book(id: String): BookDetailDto {
            val detail = api.book(id.toLong())
            val info = api.audiobookInfo(id.toLong())
            val layout = info.toLayout()
            layoutCache[id] = layout
            return detail.toDetail(info, layout)
        }

        suspend fun home(): HomeResponse {
            val continueListening = inProgress(SHELF_SIZE).map { it.toSummary() }
            val recentlyAdded =
                api.books(0, SHELF_SIZE, sort = "addedOn", dir = "desc").content.map { it.toSummary() }
            val listenAgain =
                api
                    .books(0, SHELF_SIZE, sort = "lastReadTime", dir = "desc", status = listOf("READ"))
                    .content
                    .map { it.toSummary() }
            return HomeResponse(continueListening, recentlyAdded, listenAgain)
        }

        /** Grimmory shelves, each with its audiobooks. */
        suspend fun collections(): List<CollectionDto> =
            api.shelves().map { shelf ->
                val books =
                    allPages { page -> api.books(page, GrimmoryApi.PAGE_SIZE, shelfId = shelf.id) }
                CollectionDto(shelf.id.toString(), shelf.name, books = books.map { it.toSummary() })
            }

        suspend fun collection(id: String): CollectionDto = collections().first { it.id == id }

        /** Grimmory magic (rule-based) shelves, each with its audiobooks. */
        suspend fun playlists(): List<PlaylistDto> =
            api.magicShelves().map { shelf ->
                val books =
                    allPages { page -> api.magicShelfBooks(shelf.id, page, GrimmoryApi.PAGE_SIZE) }
                        .filter { it.isAudiobook() }
                PlaylistDto(shelf.id.toString(), shelf.name, books = books.map { it.toSummary() })
            }

        suspend fun playlist(id: String): PlaylistDto = playlists().first { it.id == id }

        /** IDs of books the server considers in progress, most recent first. */
        suspend fun continueListeningIds(limit: Int): List<String> = inProgress(limit).map { it.id.toString() }

        /**
         * In-progress audiobooks, most recently played first.
         *
         * Not `/app/books/continue-listening`: on Grimmory v3.5.0 it filters by
         * `library.id IN :libraryIds` while admins get a null library set, so it
         * is always empty for admin accounts.
         */
        private suspend fun inProgress(limit: Int): List<GrimmoryBookSummary> =
            api
                .books(0, limit.coerceIn(1, GrimmoryApi.PAGE_SIZE), sort = "lastReadTime", dir = "desc", status = IN_PROGRESS)
                .content

        /** The server's audiobook progress for [bookId], or null when it has none. */
        suspend fun progress(bookId: String): ProgressDto? {
            val response = api.progress(bookId.toLong())
            val audio = response.audiobookProgress ?: return null
            val layout = layout(bookId)
            val absoluteMs = TrackTimeline.toAbsoluteMs(layout.tracks, audio.trackIndex.takeIf { layout.folderBased }, audio.positionMs ?: 0)
            val timestamp = audio.updatedAt ?: response.lastReadTime
            return ProgressDto(
                bookId = bookId,
                currentSeconds = absoluteMs / 1000.0,
                durationSeconds = layout.durationMs / 1000.0,
                finished = response.readStatus == READ,
                lastPlayedAt = timestamp,
                updatedAt = timestamp,
            )
        }

        suspend fun updateProgress(
            bookId: String,
            currentSeconds: Double,
            durationSeconds: Double,
        ) {
            val layout = layout(bookId)
            val absoluteMs = (currentSeconds * 1000).roundToLong()
            val position = TrackTimeline.toTrackPosition(layout.tracks, absoluteMs)
            val positionMs = if (layout.folderBased) position.offsetMs else absoluteMs
            val trackIndex = position.trackIndex.takeIf { layout.folderBased }
            val total = durationSeconds.takeIf { it > 0 } ?: (layout.durationMs / 1000.0)
            val finished = PlaybackRules.finishedAtPosition(currentSeconds, total)
            // Grimmory only marks a book read at 99.5%; report the app's earlier
            // finish as 100% so both agree (see PlaybackRules).
            val percent =
                when {
                    finished -> 100f
                    total > 0 -> ((currentSeconds / total) * 1000).roundToLong() / 10f
                    else -> 0f
                }
            api.updateProgress(
                bookId.toLong(),
                GrimmoryProgressUpdate(
                    fileProgress =
                        layout.bookFileId?.let {
                            GrimmoryFileProgress(
                                bookFileId = it,
                                positionData = positionMs.toString(),
                                positionHref = trackIndex?.toString(),
                                progressPercent = percent.coerceIn(0f, 100f),
                            )
                        },
                    audiobookProgress =
                        GrimmoryAudiobookProgress(
                            positionMs = positionMs,
                            trackIndex = trackIndex,
                            percentage = percent.coerceIn(0f, 100f),
                        ),
                ),
            )
        }

        suspend fun bookmarksForBook(bookId: String): List<BookmarkDto> {
            val layout = layout(bookId)
            return api.bookmarksForBook(bookId.toLong()).filter { it.positionMs != null }.map { it.toDto(layout) }
        }

        /** Creates a bookmark and returns it with its server-assigned ID. */
        suspend fun createBookmark(
            bookId: String,
            positionSeconds: Double,
            note: String?,
        ): BookmarkDto {
            val layout = layout(bookId)
            val absoluteMs = (positionSeconds * 1000).roundToLong()
            val position = TrackTimeline.toTrackPosition(layout.tracks, absoluteMs)
            val created =
                api.createBookmark(
                    GrimmoryCreateBookmark(
                        bookId = bookId.toLong(),
                        positionMs = if (layout.folderBased) position.offsetMs else absoluteMs,
                        trackIndex = position.trackIndex.takeIf { layout.folderBased },
                        title = bookmarkTitle(note, positionSeconds),
                    ),
                )
            // Creation has no notes field; Grimmory takes them on update only.
            val withNote = if (note != null) api.updateBookmark(created.id, GrimmoryUpdateBookmark(notes = note)) else created
            return withNote.toDto(layout)
        }

        suspend fun updateBookmarkNote(
            id: String,
            positionSeconds: Double,
            note: String?,
        ) {
            api.updateBookmark(id.toLong(), GrimmoryUpdateBookmark(title = bookmarkTitle(note, positionSeconds), notes = note ?: ""))
        }

        suspend fun deleteBookmark(id: String) = api.deleteBookmark(id.toLong())

        /**
         * Records one stretch of listening (a Play event up to the Pause that
         * ended it) as a Grimmory reading session.
         */
        suspend fun recordListeningStretch(
            session: PlaybackSessionEntity,
            play: PlaybackEventEntity,
            pause: PlaybackEventEntity,
        ) {
            val startMs = Iso8601.toEpochMillis(play.timestamp) ?: return
            val endMs = Iso8601.toEpochMillis(pause.timestamp) ?: return
            val seconds = ((endMs - startMs) / 1000).coerceAtLeast(0)
            if (seconds == 0L) return

            fun percent(position: Double): Float? =
                session.durationSeconds
                    .takeIf { it > 0 }
                    ?.let { (position / it * 100).toFloat().coerceIn(0f, 100f) }
            val startPercent = percent(play.positionSeconds)
            val endPercent = percent(pause.positionSeconds)
            api.recordReadingSession(
                GrimmoryReadingSession(
                    bookId = session.bookId.toLong(),
                    bookType = GrimmoryApi.AUDIOBOOK,
                    startTime = play.timestamp,
                    endTime = pause.timestamp,
                    durationSeconds = seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    durationFormatted = TimeFormat.duration(seconds),
                    startProgress = startPercent,
                    endProgress = endPercent,
                    progressDelta = if (startPercent != null && endPercent != null) endPercent - startPercent else null,
                    startLocation = TimeFormat.clock(play.positionSeconds),
                    endLocation = TimeFormat.clock(pause.positionSeconds),
                ),
            )
        }

        /**
         * The audio layout of [bookId]: from Room when the book detail is
         * cached, otherwise fetched once from the server and kept for the
         * process lifetime.
         */
        suspend fun layout(bookId: String): AudioLayout {
            val tracks = trackDao.forBook(bookId)
            val book = bookDao.get(bookId)
            if (book?.bookFileId != null && tracks.isNotEmpty()) {
                return AudioLayout(
                    bookFileId = book.bookFileId.toLongOrNull(),
                    folderBased = book.folderBased,
                    tracks = tracks.map { TrackTimeline.Track(it.index, it.startMs, it.durationMs) },
                    durationMs = (book.durationSeconds * 1000).roundToLong(),
                )
            }
            layoutCache[bookId]?.let { return it }
            return api.audiobookInfo(bookId.toLong()).toLayout().also { layoutCache[bookId] = it }
        }

        private fun bookmarkTitle(
            note: String?,
            positionSeconds: Double,
        ): String =
            note
                ?.lineSequence()
                ?.firstOrNull()
                ?.take(TITLE_MAX)
                ?.takeIf { it.isNotBlank() } ?: AUTO_TITLE_PREFIX + TimeFormat.clock(positionSeconds)

        private suspend fun allPages(fetch: suspend (Int) -> GrimmoryPage<GrimmoryBookSummary>): List<GrimmoryBookSummary> {
            val books = mutableListOf<GrimmoryBookSummary>()
            var page = 0
            while (true) {
                val result = fetch(page)
                books += result.content
                if (!result.hasNext || result.content.isEmpty() || page >= MAX_PAGES) break
                page++
            }
            return books
        }

        companion object {
            private const val SHELF_SIZE = 10
            private const val READ = "READ"
            private val IN_PROGRESS = listOf("READING", "RE_READING")
            private const val TITLE_MAX = 255
            private const val MAX_PAGES = 1_000
        }
    }

internal const val AUTO_TITLE_PREFIX = "Bookmark at "

/** How a book's audio is laid out on the server. */
data class AudioLayout(
    val bookFileId: Long?,
    val folderBased: Boolean,
    val tracks: List<TrackTimeline.Track>,
    val durationMs: Long,
)

internal fun GrimmoryAudiobookInfo.toLayout(): AudioLayout {
    val serverTracks = tracks.orEmpty()
    val timeline =
        if (folderBased && serverTracks.isNotEmpty()) {
            var cursor = 0L
            serverTracks.sortedBy { it.index }.map { track ->
                val start = track.cumulativeStartMs ?: cursor
                val duration = track.durationMs ?: 0
                cursor = start + duration
                TrackTimeline.Track(track.index, start, duration)
            }
        } else {
            listOf(TrackTimeline.Track(0, 0, durationMs ?: 0))
        }
    return AudioLayout(
        bookFileId = bookFileId,
        folderBased = folderBased,
        tracks = timeline,
        durationMs = durationMs ?: TrackTimeline.totalDurationMs(timeline),
    )
}

internal fun GrimmoryBookSummary.isAudiobook(): Boolean = primaryFileType == null || primaryFileType == GrimmoryApi.AUDIOBOOK

/** Cover version: changes whenever the cover image does, so cached files invalidate. */
private fun coverVersion(
    audiobookCoverUpdatedOn: String?,
    coverUpdatedOn: String?,
): String? = audiobookCoverUpdatedOn ?: coverUpdatedOn

internal fun GrimmoryBookSummary.toSummary(): BookSummaryDto =
    BookSummaryDto(
        id = id.toString(),
        title = title ?: primaryFileName ?: "Untitled",
        authors = authors.orEmpty().toNamed(::AuthorDto),
        narrators = splitNames(narrator).toNamed(::NarratorDto),
        genres = categories.orEmpty().toNamed(::GenreDto),
        series = seriesRefs(seriesName, seriesNumber),
        publisher = publisher?.takeIf { it.isNotBlank() }?.let { PublisherDto(it, it) },
        language = language,
        size = fileSizeKb?.times(1024),
        publishedYear = publishedDate?.take(4)?.toIntOrNull(),
        publishedDate = publishedDate,
        addedAt = addedOn,
        libraryId = libraryId?.toString(),
        hasCover = coverVersion(audiobookCoverUpdatedOn, coverUpdatedOn) != null,
        audiobookCover = audiobookCoverUpdatedOn != null,
        updatedAt = coverVersion(audiobookCoverUpdatedOn, coverUpdatedOn),
        progressFraction = readProgress?.let { (it / 100f).coerceIn(0f, 1f) },
        finished = readStatus == "READ",
    )

internal fun GrimmoryBookDetail.toDetail(
    info: GrimmoryAudiobookInfo,
    layout: AudioLayout,
): BookDetailDto {
    val serverChapters = info.chapters.orEmpty()
    val chapters =
        if (serverChapters.isNotEmpty()) {
            serverChapters.sortedBy { it.index }.map {
                ChapterDto(
                    id = "$id:${it.index}",
                    title = it.title,
                    index = it.index,
                    startSeconds = it.startTimeMs / 1000.0,
                    endSeconds = it.endTimeMs / 1000.0,
                )
            }
        } else if (layout.folderBased) {
            // Folder-based books have no embedded chapters; each track is one.
            val titles = info.tracks.orEmpty().associate { it.index to (it.title ?: it.fileName) }
            layout.tracks.map {
                ChapterDto(
                    id = "$id:${it.index}",
                    title = titles[it.index],
                    index = it.index,
                    startSeconds = it.startMs / 1000.0,
                    endSeconds = (it.startMs + it.durationMs) / 1000.0,
                )
            }
        } else {
            emptyList()
        }
    val fileNames = info.tracks.orEmpty().associate { it.index to it.fileName }
    val sizes = info.tracks.orEmpty().associate { it.index to it.fileSizeBytes }
    val audioFile = files.orEmpty().firstOrNull { it.id == info.bookFileId }
    return BookDetailDto(
        id = id.toString(),
        title = title ?: info.title ?: "Untitled",
        subtitle = subtitle,
        authors = authors.orEmpty().ifEmpty { listOfNotNull(info.author) }.toNamed(::AuthorDto),
        narrators = splitNames(narrator ?: info.narrator).toNamed(::NarratorDto),
        genres = categories.orEmpty().toNamed(::GenreDto),
        series = seriesRefs(seriesName, seriesNumber),
        durationSeconds = layout.durationMs / 1000.0,
        size = info.totalSizeBytes ?: audioFile?.fileSizeKb?.times(1024),
        publishedYear = publishedDate?.take(4)?.toIntOrNull(),
        publishedDate = publishedDate,
        addedAt = addedOn,
        libraryId = libraryId?.toString(),
        hasCover = coverVersion(audiobookCoverUpdatedOn, coverUpdatedOn) != null,
        audiobookCover = audiobookCoverUpdatedOn != null,
        updatedAt = coverVersion(audiobookCoverUpdatedOn, coverUpdatedOn),
        description = description,
        publisher = publisher?.takeIf { it.isNotBlank() }?.let { PublisherDto(it, it) },
        isbn = isbn13,
        language = language,
        chapters = chapters,
        folderBased = layout.folderBased,
        bookFileId = layout.bookFileId?.toString(),
        tracks =
            layout.tracks.map {
                TrackDto(
                    index = it.index,
                    fileName = fileNames[it.index] ?: audioFile?.fileName,
                    startMs = it.startMs,
                    durationMs = it.durationMs,
                    sizeBytes = sizes[it.index] ?: info.totalSizeBytes.takeIf { _ -> !layout.folderBased },
                )
            },
    )
}

internal fun GrimmoryBookmark.toDto(layout: AudioLayout): BookmarkDto {
    val absoluteMs = TrackTimeline.toAbsoluteMs(layout.tracks, trackIndex.takeIf { layout.folderBased }, positionMs ?: 0)
    return BookmarkDto(
        id = id.toString(),
        bookId = bookId.toString(),
        positionSeconds = absoluteMs / 1000.0,
        // Grimmory requires a title; ours are generated from the position when
        // the user left the note blank, so those are not shown as notes.
        note = notes?.takeIf { it.isNotBlank() } ?: title?.takeIf { it.isNotBlank() && !it.startsWith(AUTO_TITLE_PREFIX) },
        updatedAt = updatedAt,
    )
}

private fun <T> List<String>.toNamed(build: (String, String) -> T): List<T> = filter { it.isNotBlank() }.distinct().map { build(it.trim(), it.trim()) }

/** Grimmory stores narrators as one free-text field; split the usual separators. */
internal fun splitNames(value: String?): List<String> =
    value
        ?.split(',', ';', '&')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

private fun seriesRefs(
    name: String?,
    number: Float?,
): List<SeriesRefDto> =
    listOfNotNull(
        name?.takeIf { it.isNotBlank() }?.let { SeriesRefDto(it, it, number?.let(::formatSequence)) },
    )

internal fun formatSequence(number: Float): String = if (number % 1f == 0f) number.toLong().toString() else String.format(Locale.ROOT, "%s", number)

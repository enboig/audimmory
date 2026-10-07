package org.audimmory.mobile.data.remote

/**
 * App-level transfer models produced by [GrimmoryClient] and consumed by the
 * repositories. They are not wire formats (those live in `GrimmoryDtos.kt`):
 * IDs are strings, positions are seconds on the book's continuous timeline,
 * and normalized metadata uses the name as its ID because Grimmory stores
 * authors, narrators, genres, series and publishers as plain names.
 */

data class AccountDto(
    val username: String,
    val displayName: String,
    val canDownload: Boolean,
    val serverVersion: String?,
)

data class LibraryDto(
    val id: String,
    val name: String,
)

data class BookSummaryDto(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val authors: List<AuthorDto> = emptyList(),
    val narrators: List<NarratorDto> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val series: List<SeriesRefDto> = emptyList(),
    val publisher: PublisherDto? = null,
    val language: String? = null,
    val durationSeconds: Double = 0.0,
    val size: Long? = null,
    val publishedYear: Int? = null,
    val publishedDate: String? = null,
    val addedAt: String? = null,
    val libraryId: String? = null,
    val hasCover: Boolean = false,
    /** True when the cover comes from Grimmory's dedicated audiobook cover. */
    val audiobookCover: Boolean = false,
    /** Cover version; changes whenever the cover image does. */
    val updatedAt: String? = null,
    /** Server-side listening progress 0..1, when the list endpoint reports it. */
    val progressFraction: Float? = null,
    val finished: Boolean = false,
)

data class HomeResponse(
    val continueListening: List<BookSummaryDto> = emptyList(),
    val recentlyAdded: List<BookSummaryDto> = emptyList(),
    val listenAgain: List<BookSummaryDto> = emptyList(),
)

data class BookDetailDto(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val authors: List<AuthorDto> = emptyList(),
    val narrators: List<NarratorDto> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val series: List<SeriesRefDto> = emptyList(),
    val durationSeconds: Double = 0.0,
    val size: Long? = null,
    val publishedYear: Int? = null,
    val publishedDate: String? = null,
    val addedAt: String? = null,
    val libraryId: String? = null,
    val hasCover: Boolean = false,
    val audiobookCover: Boolean = false,
    val updatedAt: String? = null,
    val description: String? = null,
    val publisher: PublisherDto? = null,
    val isbn: String? = null,
    val language: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
    /** True for a directory of audio files (usually mp3) served track by track. */
    val folderBased: Boolean = false,
    /** Grimmory's ID for the audio file, needed when saving progress. */
    val bookFileId: String? = null,
    val tracks: List<TrackDto> = emptyList(),
)

/** A series a book belongs to, with this book's sequence within it. */
data class SeriesRefDto(
    val id: String,
    val name: String,
    val sequence: String? = null,
)

data class AuthorDto(
    val id: String,
    val name: String,
)

data class NarratorDto(
    val id: String,
    val name: String,
)

data class GenreDto(
    val id: String,
    val name: String,
)

data class PublisherDto(
    val id: String,
    val name: String,
)

data class ChapterDto(
    val id: String,
    val title: String? = null,
    val index: Int,
    val startSeconds: Double,
    val endSeconds: Double,
)

/** One audio file of a book, positioned on the book's continuous timeline. */
data class TrackDto(
    val index: Int,
    val fileName: String?,
    val startMs: Long,
    val durationMs: Long,
    val sizeBytes: Long?,
)

data class ProgressDto(
    val bookId: String,
    val currentSeconds: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val finished: Boolean = false,
    val finishedAt: String? = null,
    val startedAt: String? = null,
    val lastPlayedAt: String? = null,
    val updatedAt: String? = null,
)

data class BookmarkDto(
    val id: String,
    val bookId: String,
    val positionSeconds: Double,
    val note: String? = null,
    val updatedAt: String? = null,
)

data class SeriesDto(
    val id: String,
    val name: String,
    val books: List<SeriesBookDto> = emptyList(),
)

data class SeriesBookDto(
    val book: BookSummaryDto,
    val sequence: String?,
)

data class CollectionDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val libraryId: String? = null,
    val books: List<BookSummaryDto> = emptyList(),
    val updatedAt: String? = null,
)

data class PlaylistDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val books: List<BookSummaryDto> = emptyList(),
    val updatedAt: String? = null,
)

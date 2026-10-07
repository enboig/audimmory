package org.audimmory.mobile.data.repository

import org.audimmory.mobile.data.local.BookEntity
import org.audimmory.mobile.data.local.BookFacetEntity
import org.audimmory.mobile.data.local.ChapterEntity
import org.audimmory.mobile.data.local.ProgressEntity
import org.audimmory.mobile.data.local.TrackEntity
import org.audimmory.mobile.data.remote.AuthorDto
import org.audimmory.mobile.data.remote.BookDetailDto
import org.audimmory.mobile.data.remote.BookSummaryDto
import org.audimmory.mobile.data.remote.ChapterDto
import org.audimmory.mobile.data.remote.GenreDto
import org.audimmory.mobile.data.remote.NarratorDto
import org.audimmory.mobile.data.remote.ProgressDto
import org.audimmory.mobile.data.remote.PublisherDto
import org.audimmory.mobile.data.remote.SeriesRefDto
import org.audimmory.mobile.data.remote.TrackDto

/** Conversions between app transfer models and local Room entities. */

private fun cachedCoverPath(
    existing: BookEntity?,
    hasCover: Boolean,
    updatedAt: String?,
): String? = existing?.takeIf { hasCover && it.coverUpdatedAt == updatedAt }?.coverLocalPath

private fun cachedCoverUpdatedAt(
    existing: BookEntity?,
    hasCover: Boolean,
    updatedAt: String?,
): String? = existing?.takeIf { hasCover && it.coverUpdatedAt == updatedAt }?.coverUpdatedAt

/**
 * List endpoints carry no duration or audio layout, so a summary keeps what a
 * previously fetched detail stored.
 */
fun BookSummaryDto.toEntity(existing: BookEntity? = null): BookEntity =
    BookEntity(
        id = id,
        title = title,
        subtitle = subtitle ?: existing?.subtitle,
        authors = authors.joinToString(", ") { it.name }.ifEmpty { null },
        narrators = narrators.joinToString(", ") { it.name }.ifEmpty { null },
        durationSeconds = durationSeconds.takeIf { it > 0 } ?: existing?.durationSeconds ?: 0.0,
        size = size,
        publishedYear = publishedYear,
        publishedDate = publishedDate,
        addedAt = addedAt,
        fileModified = null,
        libraryId = libraryId,
        hasCover = hasCover,
        audiobookCover = audiobookCover,
        coverLocalPath = cachedCoverPath(existing, hasCover, updatedAt),
        coverUpdatedAt = cachedCoverUpdatedAt(existing, hasCover, updatedAt),
        description = existing?.description,
        publisher = publisher?.name,
        language = language,
        updatedAt = updatedAt,
        folderBased = existing?.folderBased ?: false,
        bookFileId = existing?.bookFileId,
    )

fun BookDetailDto.toEntity(existing: BookEntity? = null): BookEntity =
    BookEntity(
        id = id,
        title = title,
        subtitle = subtitle,
        authors = authors.joinToString(", ") { it.name }.ifEmpty { null },
        narrators = narrators.joinToString(", ") { it.name }.ifEmpty { null },
        durationSeconds = durationSeconds,
        size = size,
        publishedYear = publishedYear,
        publishedDate = publishedDate,
        addedAt = addedAt,
        fileModified = null,
        libraryId = libraryId,
        hasCover = hasCover,
        audiobookCover = audiobookCover,
        coverLocalPath = cachedCoverPath(existing, hasCover, updatedAt),
        coverUpdatedAt = cachedCoverUpdatedAt(existing, hasCover, updatedAt),
        description = description ?: existing?.description,
        publisher = publisher?.name,
        language = language,
        updatedAt = updatedAt,
        folderBased = folderBased,
        bookFileId = bookFileId,
    )

fun BookSummaryDto.toFacetEntities(): List<BookFacetEntity> = facetEntities(id, authors, narrators, genres, series, publisher, language)

fun BookDetailDto.toFacetEntities(): List<BookFacetEntity> = facetEntities(id, authors, narrators, genres, series, publisher, language)

private fun facetEntities(
    bookId: String,
    authors: List<AuthorDto>,
    narrators: List<NarratorDto>,
    genres: List<GenreDto>,
    series: List<SeriesRefDto>,
    publisher: PublisherDto?,
    language: String?,
): List<BookFacetEntity> =
    facets(bookId, "author", authors.map { it.id to it.name }) +
        facets(bookId, "narrator", narrators.map { it.id to it.name }) +
        facets(bookId, "genre", genres.map { it.id to it.name }) +
        facets(bookId, "series", series.map { it.id to it.name }) +
        facets(bookId, "publisher", listOfNotNull(publisher?.let { it.id to it.name })) +
        facets(bookId, "language", listOfNotNull(language?.takeIf { it.isNotBlank() }?.let { it to it }))

private fun facets(
    bookId: String,
    category: String,
    values: List<Pair<String, String>>,
): List<BookFacetEntity> =
    values.mapIndexed { index, (id, name) ->
        BookFacetEntity(
            bookId = bookId,
            category = category,
            facetId = id,
            name = name,
            position = index,
        )
    }

fun ChapterDto.toEntity(bookId: String): ChapterEntity =
    ChapterEntity(
        id = id,
        bookId = bookId,
        title = title,
        index = index,
        startSeconds = startSeconds,
        endSeconds = endSeconds,
    )

fun TrackDto.toEntity(
    bookId: String,
    title: String?,
): TrackEntity =
    TrackEntity(
        bookId = bookId,
        index = index,
        title = title,
        fileName = fileName,
        startMs = startMs,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
    )

fun ProgressDto.toEntity(dirty: Boolean = false): ProgressEntity =
    ProgressEntity(
        bookId = bookId,
        currentSeconds = currentSeconds,
        durationSeconds = durationSeconds,
        finished = finished,
        startedAt = startedAt,
        finishedAt = finishedAt,
        lastPlayedAt = lastPlayedAt,
        updatedAt = updatedAt,
        dirty = dirty,
    )

package org.audimmory.mobile.data.repository

import org.audimmory.mobile.data.remote.AuthorDto
import org.audimmory.mobile.data.remote.BookDetailDto
import org.audimmory.mobile.data.remote.BookSummaryDto
import org.audimmory.mobile.data.remote.GenreDto
import org.audimmory.mobile.data.remote.NarratorDto
import org.audimmory.mobile.data.remote.PublisherDto
import org.audimmory.mobile.data.remote.SeriesRefDto
import org.junit.Assert.assertEquals
import org.junit.Test

class BookMetadataMapperTest {
    @Test
    fun `structured metadata preserves display order and facet ids`() {
        val dto =
            BookSummaryDto(
                id = "book",
                title = "Book",
                authors = listOf(AuthorDto("author", "Author")),
                narrators =
                    listOf(
                        NarratorDto("narrator-1", "Primary Reader"),
                        NarratorDto("narrator-2", "Doe, Jane"),
                    ),
                genres = listOf(GenreDto("genre", "Fantasy")),
                series = listOf(SeriesRefDto("series", "Saga")),
                publisher = PublisherDto("publisher", "Publisher"),
                language = "English",
                size = 1234,
                addedAt = "2025-01-01T00:00:00Z",
            )

        assertEquals("Primary Reader, Doe, Jane", dto.toEntity().narrators)
        assertEquals("Publisher", dto.toEntity().publisher)
        assertEquals(1234L, dto.toEntity().size)
        assertEquals("2025-01-01T00:00:00Z", dto.toEntity().addedAt)
        assertEquals(
            listOf("author", "narrator-1", "narrator-2", "genre", "series", "publisher", "English"),
            dto.toFacetEntities().map { it.facetId },
        )
        assertEquals(listOf(0, 0, 1, 0, 0, 0, 0), dto.toFacetEntities().map { it.position })
        assertEquals("publisher", dto.toFacetEntities()[5].category)
        assertEquals("language", dto.toFacetEntities().last().category)
    }

    @Test
    fun `detail publisher maps to the entity display string`() {
        val dto =
            BookDetailDto(
                id = "book",
                title = "Book",
                publisher = PublisherDto("publisher", "Publisher"),
            )

        assertEquals("Publisher", dto.toEntity().publisher)
    }

    @Test
    fun `summary refresh keeps duration and audio layout from a cached detail`() {
        val existing =
            BookDetailDto(
                id = "book",
                title = "Book",
                durationSeconds = 90.0,
                folderBased = true,
                bookFileId = "7",
            ).toEntity()

        val refreshed = BookSummaryDto(id = "book", title = "Book").toEntity(existing)

        assertEquals(90.0, refreshed.durationSeconds, 0.0)
        assertEquals(true, refreshed.folderBased)
        assertEquals("7", refreshed.bookFileId)
    }

    @Test
    fun `series are derived from books and ordered by sequence`() {
        val books =
            listOf(
                BookSummaryDto(id = "b", title = "Second", series = listOf(SeriesRefDto("Saga", "Saga", "2"))),
                BookSummaryDto(id = "a", title = "First", series = listOf(SeriesRefDto("Saga", "Saga", "1"))),
                BookSummaryDto(id = "c", title = "Loose"),
            )

        val (series, members) = seriesFrom(books)

        assertEquals(listOf("Saga"), series.map { it.id })
        assertEquals(listOf("a", "b"), members.sortedBy { it.position }.map { it.bookId })
        assertEquals(listOf("1", "2"), members.sortedBy { it.position }.map { it.sequence })
    }
}

package org.audimmory.mobile.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decodes Grimmory v3.5.0 payloads (main API shapes) and checks the app mapping. */
class GrimmoryMappingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val folderInfo =
        """
        {"bookId":2,"bookFileId":2,"title":"Folder MP3","author":"Bob Writer","narrator":null,
         "durationMs":90121,"bitrate":64,"codec":"mp3","folderBased":true,"chapters":null,
         "tracks":[
           {"index":0,"fileName":"01 - Part 1.mp3","title":"Part 1","durationMs":25051,"fileSizeBytes":200738,"cumulativeStartMs":0},
           {"index":1,"fileName":"02 - Part 2.mp3","title":"Part 2","durationMs":30040,"fileSizeBytes":240653,"cumulativeStartMs":25051},
           {"index":2,"fileName":"03 - Part 3.mp3","title":"Part 3","durationMs":35030,"fileSizeBytes":280568,"cumulativeStartMs":55091}]}
        """.trimIndent()

    private val m4bInfo =
        """
        {"bookId":1,"bookFileId":1,"title":"Chaptered M4B","author":"Ann Author","durationMs":120000,
         "totalSizeBytes":746496,"folderBased":false,"tracks":null,
         "chapters":[{"index":0,"title":"Opening","startTimeMs":0,"endTimeMs":40000,"durationMs":40000},
                     {"index":1,"title":"Middle","startTimeMs":40000,"endTimeMs":90000,"durationMs":50000}]}
        """.trimIndent()

    private val folderDetail =
        """
        {"id":2,"libraryId":1,"libraryName":"Audio","addedOn":"2026-10-07T17:28:39Z",
         "primaryFile":{"id":2,"bookId":2,"fileName":"Folder MP3","folderBased":true,"bookType":"AUDIOBOOK","fileSizeKb":705,"book":true},
         "metadata":{"bookId":2,"title":"Folder MP3","authors":["Bob Writer"],"seriesName":"Tones","seriesNumber":2.0,
                     "categories":["Test"],"language":"en","audiobookCoverUpdatedOn":"2026-10-07T18:00:00Z"},
         "shelves":[],"readStatus":"UNSET"}
        """.trimIndent()

    @Test
    fun `folder-based book uses tracks as timeline and chapters`() {
        val info = json.decodeFromString<GrimmoryAudiobookInfo>(folderInfo)
        val layout = info.toLayout()
        val detail = json.decodeFromString<GrimmoryBook>(folderDetail).toDetail(info, layout)

        assertTrue(detail.folderBased)
        assertEquals("2", detail.bookFileId)
        assertEquals(90.121, detail.durationSeconds, 0.0001)
        assertEquals(listOf("Part 1", "Part 2", "Part 3"), detail.chapters.map { it.title })
        assertEquals(25.051, detail.chapters[1].startSeconds, 0.0001)
        assertEquals(listOf("01 - Part 1.mp3", "02 - Part 2.mp3", "03 - Part 3.mp3"), detail.tracks.map { it.fileName })
        assertEquals(240_653L, detail.tracks[1].sizeBytes)
        assertEquals(listOf(SeriesRefDto("Tones", "Tones", "2")), detail.series)
        assertTrue(detail.hasCover)
        assertTrue(detail.audiobookCover)
        assertEquals("2026-10-07T18:00:00Z", detail.updatedAt)
    }

    @Test
    fun `single-file book keeps embedded chapters and one track`() {
        val info = json.decodeFromString<GrimmoryAudiobookInfo>(m4bInfo)
        val layout = info.toLayout()
        assertFalse(layout.folderBased)
        assertEquals(1, layout.tracks.size)
        assertEquals(120_000L, layout.tracks.single().durationMs)

        val detail = GrimmoryBook(id = 1, metadata = GrimmoryBookMetadata(title = "Chaptered M4B")).toDetail(info, layout)
        assertEquals(listOf("Opening", "Middle"), detail.chapters.map { it.title })
        assertEquals(746_496L, detail.tracks.single().sizeBytes)
        assertFalse(detail.hasCover)
    }

    @Test
    fun `folder bookmark positions are converted to the book timeline`() {
        val layout = json.decodeFromString<GrimmoryAudiobookInfo>(folderInfo).toLayout()
        val bookmark =
            json.decodeFromString<GrimmoryBookmark>(
                """{"id":7,"userId":1,"bookId":2,"cfi":null,"positionMs":5000,"trackIndex":1,"title":"t","notes":null}""",
            )
        val dto = bookmark.toDto(layout)
        assertEquals("7", dto.id)
        assertEquals(30.051, dto.positionSeconds, 0.0001)
        assertEquals("t", dto.note)
    }

    @Test
    fun `summary maps names as facet ids and splits narrators`() {
        val summary =
            json
                .decodeFromString<GrimmoryBook>(
                    """{"id":3,"primaryFile":{"id":3,"fileName":"single.mp3","bookType":"AUDIOBOOK","fileSizeKb":2},
                       "metadata":{"title":"Single MP3","authors":["Ann Author"],"narrator":"A, B & C","publisher":"Pub"},
                       "audiobookProgress":{"positionMs":5000,"percentage":42.5},"readStatus":"READING"}""",
                ).toSummary()
        assertEquals("Single MP3", summary.title)
        assertEquals(listOf(AuthorDto("Ann Author", "Ann Author")), summary.authors)
        assertEquals(listOf("A", "B", "C"), summary.narrators.map { it.id })
        assertEquals(PublisherDto("Pub", "Pub"), summary.publisher)
        assertEquals(0.425f, summary.progressFraction!!, 0.0001f)
        assertEquals(2048L, summary.size)
        assertNull(summary.updatedAt)
    }

    @Test
    fun `audio file is found among the alternative formats of an ebook`() {
        val book =
            json.decodeFromString<GrimmoryBook>(
                """{"id":4,"primaryFile":{"id":10,"fileName":"book.epub","bookType":"EPUB","fileSizeKb":300},
                   "alternativeFormats":[{"id":11,"fileName":"book.m4b","bookType":"AUDIOBOOK","fileSizeKb":5000}]}""",
            )
        assertEquals(5000L * 1024, book.toSummary().size)
        assertEquals("book.m4b", book.toSummary().title)

        val info = json.decodeFromString<GrimmoryAudiobookInfo>(m4bInfo.replace("\"bookFileId\":1", "\"bookFileId\":11"))
        val detail = book.toDetail(info, info.toLayout())
        assertEquals("11", detail.bookFileId)
        assertEquals("book.m4b", detail.tracks.single().fileName)
    }

    @Test
    fun `browse page keeps its paging metadata`() {
        val page =
            json.decodeFromString<GrimmoryBrowsePage<GrimmoryBook>>(
                """{"content":[{"id":1},{"id":2}],"page":{"number":0,"size":2,"totalElements":3,"totalPages":2,"cursor":"x"},
                   "links":[{"rel":"next","href":"/api/v1/books/page?cursor=y"}]}""",
            )
        assertEquals(listOf(1L, 2L), page.content.map { it.id })
        assertEquals(2, page.page.totalPages)
    }

    @Test
    fun `admin permission uses Lombok property name`() {
        val user =
            json.decodeFromString<GrimmoryUser>(
                """{"id":1,"username":"admin","permissions":{"admin":true,"canDownload":false}}""",
            )
        assertTrue(user.permissions!!.isAdmin)
    }
}

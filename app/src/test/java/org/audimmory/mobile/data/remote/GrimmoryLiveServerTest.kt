package org.audimmory.mobile.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.audimmory.mobile.data.local.BookDao
import org.audimmory.mobile.data.local.BookEntity
import org.audimmory.mobile.data.local.PlaybackEventEntity
import org.audimmory.mobile.data.local.PlaybackSessionEntity
import org.audimmory.mobile.data.local.TrackDao
import org.audimmory.mobile.data.local.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.time.Instant

/**
 * Exercises [GrimmoryClient] against a real Grimmory server. Skipped unless
 * `GRIMMORY_URL`, `GRIMMORY_USER` and `GRIMMORY_PASSWORD` are set, so CI and
 * normal test runs never need a server.
 *
 * Expects a library containing at least one single-file audiobook with
 * chapters and one folder-based audiobook with several tracks, e.g. the
 * fixtures described in AGENTS.md.
 */
class GrimmoryLiveServerTest {
    private val baseUrl = System.getenv("GRIMMORY_URL")
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    private var token: String? = null
    private lateinit var http: OkHttpClient
    private lateinit var client: GrimmoryClient

    @Before
    fun setUp() {
        assumeTrue("GRIMMORY_URL not set", !baseUrl.isNullOrBlank())
        http =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    chain.proceed(token?.let { request.newBuilder().header("Authorization", "Bearer $it").build() } ?: request)
                }.build()
        val api =
            Retrofit
                .Builder()
                .baseUrl(baseUrl.trimEnd('/') + "/")
                .client(http)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(GrimmoryApi::class.java)
        client = GrimmoryClient(api, EmptyBookDao, EmptyTrackDao)
    }

    private suspend fun signIn() {
        token = client.login(System.getenv("GRIMMORY_USER"), System.getenv("GRIMMORY_PASSWORD")).accessToken
    }

    @Test
    fun `account libraries and audiobooks`() =
        runTest {
            signIn()
            val account = client.me()
            assertTrue(account.username.isNotBlank())
            assertTrue(client.libraries().isNotEmpty())
            val books = client.books()
            assertTrue(books.isNotEmpty())
            client.home()
        }

    @Test
    fun `folder-based progress round-trips through track positions`() =
        runTest {
            signIn()
            val folder = client.books().map { client.book(it.id) }.first { it.folderBased && it.tracks.size > 1 }
            val second = folder.tracks[1]
            val target = (second.startMs + 1_500) / 1000.0

            client.updateProgress(folder.id, target, folder.durationSeconds)
            val progress = client.progress(folder.id)
            assertNotNull(progress)
            assertEquals(target, progress!!.currentSeconds, 0.01)

            // The server must have stored a per-track position, like the web player does.
            val raw = rawJson("/api/v1/app/books/${folder.id}/progress")
            assertTrue(raw, raw.contains("\"trackIndex\":1"))
            assertTrue(raw, raw.contains("\"positionMs\":1500"))
        }

    @Test
    fun `a book with progress appears on the continue listening shelf`() =
        runTest {
            signIn()
            val book = client.books().first()
            client.updateProgress(book.id, 10.0, client.book(book.id).durationSeconds)
            assertTrue(client.home().continueListening.any { it.id == book.id })
            assertTrue(book.id in client.continueListeningIds(25))
        }

    @Test
    fun `single-file book exposes chapters and absolute progress`() =
        runTest {
            signIn()
            val single = client.books().map { client.book(it.id) }.first { !it.folderBased && it.chapters.size > 1 }
            assertEquals(1, single.tracks.size)
            client.updateProgress(single.id, 61.0, single.durationSeconds)
            assertEquals(61.0, client.progress(single.id)!!.currentSeconds, 0.01)
        }

    @Test
    fun `bookmarks create list and delete in book time`() =
        runTest {
            signIn()
            val folder = client.books().map { client.book(it.id) }.first { it.folderBased && it.tracks.size > 2 }
            val position = (folder.tracks[2].startMs + 2_000) / 1000.0

            val created = client.createBookmark(folder.id, position, "live test note")
            val listed = client.bookmarksForBook(folder.id).first { it.id == created.id }
            assertEquals(position, listed.positionSeconds, 0.01)
            assertEquals("live test note", listed.note)

            client.deleteBookmark(created.id)
            assertTrue(client.bookmarksForBook(folder.id).none { it.id == created.id })
        }

    @Test
    fun `listening stretch is accepted as a reading session`() =
        runTest {
            signIn()
            val book = client.books().first()
            val end = Instant.now()
            val start = end.minusSeconds(90)
            val session =
                PlaybackSessionEntity(
                    id = "s",
                    bookId = book.id,
                    title = null,
                    authors = null,
                    playMethod = "Direct Play",
                    deviceInfo = "test",
                    startedAt = start.toString(),
                    updatedAt = end.toString(),
                    endedAt = null,
                    timeListenedSeconds = 90,
                    lastPositionSeconds = 100.0,
                    durationSeconds = 200.0,
                )

            fun event(
                name: String,
                at: Instant,
                position: Double,
            ) = PlaybackEventEntity(name, "s", book.id, name, "Playback", position, at.toString())
            client.recordListeningStretch(session, event("Play", start, 10.0), event("Pause", end, 100.0))
        }

    private fun rawJson(path: String): String =
        http
            .newCall(Request.Builder().url(baseUrl.trimEnd('/') + path).build())
            .execute()
            .use { it.body!!.string() }

    private object EmptyBookDao : BookDao {
        override fun observeAll(): Flow<List<BookEntity>> = flowOf(emptyList())

        override fun observeByLibrary(libraryId: String): Flow<List<BookEntity>> = flowOf(emptyList())

        override fun observe(id: String): Flow<BookEntity?> = flowOf(null)

        override suspend fun get(id: String): BookEntity? = null

        override suspend fun upsertAll(books: List<BookEntity>) = Unit

        override suspend fun upsert(book: BookEntity) = Unit

        override suspend fun deleteAll() = Unit
    }

    private object EmptyTrackDao : TrackDao {
        override suspend fun forBook(bookId: String): List<TrackEntity> = emptyList()

        override suspend fun deleteForBook(bookId: String) = Unit

        override suspend fun insertAll(tracks: List<TrackEntity>) = Unit
    }
}

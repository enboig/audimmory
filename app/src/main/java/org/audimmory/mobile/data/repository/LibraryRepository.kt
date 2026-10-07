package org.audimmory.mobile.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import org.audimmory.mobile.data.local.AudimmoryDatabase
import org.audimmory.mobile.data.local.BookDao
import org.audimmory.mobile.data.local.BookEntity
import org.audimmory.mobile.data.local.BookFacetDao
import org.audimmory.mobile.data.local.BookFacetEntity
import org.audimmory.mobile.data.local.CachedLibraryDao
import org.audimmory.mobile.data.local.CachedLibraryEntity
import org.audimmory.mobile.data.local.ChapterDao
import org.audimmory.mobile.data.local.ChapterEntity
import org.audimmory.mobile.data.local.ProgressDao
import org.audimmory.mobile.data.local.ProgressEntity
import org.audimmory.mobile.data.local.SeriesBookEntity
import org.audimmory.mobile.data.local.SeriesDao
import org.audimmory.mobile.data.local.SeriesEntity
import org.audimmory.mobile.data.local.TrackDao
import org.audimmory.mobile.data.local.TrackEntity
import org.audimmory.mobile.data.remote.BookDetailDto
import org.audimmory.mobile.data.remote.BookSummaryDto
import org.audimmory.mobile.data.remote.GrimmoryClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first library access: the UI observes Room, while [refreshBooks]
 * pulls from the server and updates the local cache. Reads never require the
 * network.
 */
@Singleton
class LibraryRepository
    @Inject
    constructor(
        private val client: GrimmoryClient,
        private val database: AudimmoryDatabase,
        private val bookDao: BookDao,
        private val bookFacetDao: BookFacetDao,
        private val cachedLibraryDao: CachedLibraryDao,
        private val cacheCoordinator: CacheCoordinator,
        private val chapterDao: ChapterDao,
        private val trackDao: TrackDao,
        private val progressDao: ProgressDao,
        private val seriesDao: SeriesDao,
        private val connectionStatusRepository: ConnectionStatusRepository,
    ) {
        fun observeBooks(): Flow<List<BookEntity>> = bookDao.observeAll()

        fun observeBookFacets(): Flow<List<BookFacetEntity>> = bookFacetDao.observeAll()

        fun observeBookFacets(bookId: String): Flow<List<BookFacetEntity>> = bookFacetDao.observeForBook(bookId)

        fun observeLibraries(): Flow<List<CachedLibraryEntity>> = cachedLibraryDao.observeAll()

        fun observeBook(id: String): Flow<BookEntity?> = bookDao.observe(id)

        suspend fun getBook(id: String): BookEntity? = bookDao.get(id)

        suspend fun getTracks(bookId: String): List<TrackEntity> = trackDao.forBook(bookId)

        fun observeChapters(bookId: String): Flow<List<ChapterEntity>> = chapterDao.observeForBook(bookId)

        fun observeProgress(bookId: String): Flow<ProgressEntity?> = progressDao.observe(bookId)

        fun observeAllProgress(): Flow<List<ProgressEntity>> = progressDao.observeAll()

        /**
         * Pulls every audiobook from the server into the local cache. Series are
         * derived from the same list (Grimmory series are just names on books),
         * so they are replaced here too.
         */
        suspend fun refreshBooks(): Result<Unit> {
            val result =
                runCatching {
                    cacheCoordinator.exclusive {
                        val libraries = client.libraries()
                        val books = client.books()
                        val facets = books.flatMap { it.toFacetEntities() }
                        val cachedLibraries = libraries.map { CachedLibraryEntity(it.id, it.name) }
                        val (series, members) = seriesFrom(books)

                        database.withTransaction {
                            val entities = books.map { it.toEntity(bookDao.get(it.id)) }
                            bookDao.deleteAll()
                            bookDao.upsertAll(entities)
                            bookFacetDao.replaceAll(facets)
                            cachedLibraryDao.replaceAll(cachedLibraries)
                            seriesDao.replaceAll(series, members)
                        }
                    }
                }
            result
                .onSuccess { connectionStatusRepository.markServerSuccess() }
                .onFailure { connectionStatusRepository.markServerFailure() }
            return result
        }

        /** Pulls a single book's detail (incl. chapters, tracks, progress) into the cache. */
        suspend fun refreshBook(id: String): Result<Unit> = refreshBookDetail(id).map { }

        /**
         * Like [refreshBook] but returns the fetched detail so callers can read
         * fields not persisted on [BookEntity] (e.g. series membership).
         */
        suspend fun refreshBookDetail(id: String): Result<BookDetailDto> {
            val result =
                runCatching {
                    cacheCoordinator.exclusive {
                        val detail = client.book(id)
                        val progress = runCatching { client.progress(id) }.getOrNull()
                        val chapterTitles = detail.chapters.associate { it.index to it.title }
                        database.withTransaction {
                            val existing = bookDao.get(id)
                            bookDao.upsert(detail.toEntity(existing))
                            bookFacetDao.replaceForBook(id, detail.toFacetEntities())
                            chapterDao.replaceForBook(id, detail.chapters.map { it.toEntity(id) })
                            trackDao.replaceForBook(
                                id,
                                detail.tracks.map { it.toEntity(id, chapterTitles[it.index].takeIf { detail.folderBased }) },
                            )
                            progress?.let {
                                // Do not clobber unsynced local playback with stale server progress.
                                if (progressDao.get(id)?.dirty != true) {
                                    progressDao.upsert(it.toEntity())
                                }
                            }
                        }
                        detail
                    }
                }
            result
                .onSuccess { connectionStatusRepository.markServerSuccess() }
                .onFailure { connectionStatusRepository.markServerFailure() }
            return result
        }
    }

/** Groups books by series name, ordered by sequence number then title. */
internal fun seriesFrom(books: List<BookSummaryDto>): Pair<List<SeriesEntity>, List<SeriesBookEntity>> {
    val grouped =
        books
            .flatMap { book -> book.series.map { ref -> ref to book } }
            .groupBy { (ref, _) -> ref.id }
    val series = grouped.map { (id, pairs) -> SeriesEntity(id = id, name = pairs.first().first.name) }
    val members =
        grouped.flatMap { (id, pairs) ->
            pairs
                .sortedWith(compareBy({ it.first.sequence?.toDoubleOrNull() ?: Double.MAX_VALUE }, { it.second.title.lowercase() }))
                .mapIndexed { index, (ref, book) ->
                    SeriesBookEntity(seriesId = id, bookId = book.id, sequence = ref.sequence, position = index)
                }
        }
    return series to members
}

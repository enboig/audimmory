package org.audimmory.mobile.data.repository

import kotlinx.coroutines.flow.Flow
import org.audimmory.mobile.data.local.BookEntity
import org.audimmory.mobile.data.local.MemberCoverRow
import org.audimmory.mobile.data.local.SeriesBookEntity
import org.audimmory.mobile.data.local.SeriesDao
import org.audimmory.mobile.data.local.SeriesEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first browse access to series. Grimmory series are names on books,
 * so they are derived from the audiobook list during
 * [LibraryRepository.refreshBooks]; refreshing a series refreshes the library.
 * Series are read-only on mobile.
 */
@Singleton
class SeriesRepository
    @Inject
    constructor(
        private val seriesDao: SeriesDao,
        private val libraryRepository: LibraryRepository,
    ) {
        fun observeAll(): Flow<List<SeriesEntity>> = seriesDao.observeAll()

        fun observe(id: String): Flow<SeriesEntity?> = seriesDao.observe(id)

        fun observeBooks(seriesId: String): Flow<List<BookEntity>> = seriesDao.observeBooks(seriesId)

        fun observeMembers(seriesId: String): Flow<List<SeriesBookEntity>> = seriesDao.observeMembers(seriesId)

        fun observeMemberPreviews(): Flow<List<MemberCoverRow>> = seriesDao.observeMemberPreviews()

        suspend fun refreshAll(): Result<Unit> = libraryRepository.refreshBooks()

        @Suppress("UNUSED_PARAMETER")
        suspend fun refresh(id: String): Result<Unit> = libraryRepository.refreshBooks()
    }

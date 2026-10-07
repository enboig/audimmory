package org.audimmory.mobile.data.repository

import kotlinx.coroutines.flow.Flow
import org.audimmory.mobile.data.local.BookDao
import org.audimmory.mobile.data.local.BookEntity
import org.audimmory.mobile.data.local.CollectionBookEntity
import org.audimmory.mobile.data.local.CollectionDao
import org.audimmory.mobile.data.local.CollectionEntity
import org.audimmory.mobile.data.local.MemberCoverRow
import org.audimmory.mobile.data.remote.CollectionDto
import org.audimmory.mobile.data.remote.GrimmoryClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first browse access to collections, which are Grimmory **shelves**
 * (only their audiobooks are kept). The UI observes Room; [refresh] pulls from
 * the server. Read-only on mobile.
 */
@Singleton
class CollectionRepository
    @Inject
    constructor(
        private val client: GrimmoryClient,
        private val collectionDao: CollectionDao,
        private val bookDao: BookDao,
        private val cacheCoordinator: CacheCoordinator,
        private val connectionStatusRepository: ConnectionStatusRepository,
    ) {
        fun observeAll(): Flow<List<CollectionEntity>> = collectionDao.observeAll()

        fun observe(id: String): Flow<CollectionEntity?> = collectionDao.observe(id)

        fun observeAllMembers(): Flow<List<CollectionBookEntity>> = collectionDao.observeAllMembers()

        fun observeBooks(collectionId: String): Flow<List<BookEntity>> = collectionDao.observeBooks(collectionId)

        fun observeMemberPreviews(): Flow<List<MemberCoverRow>> = collectionDao.observeMemberPreviews()

        suspend fun refreshAll(): Result<Unit> {
            val result =
                runCatching {
                    cacheCoordinator.exclusive {
                        val collections = client.collections()
                        val books = collections.flatMap { it.books }.distinctBy { it.id }
                        bookDao.upsertAll(books.map { it.toEntity(bookDao.get(it.id)) })

                        val entities =
                            collections.map {
                                CollectionEntity(it.id, it.name, it.description, it.libraryId, it.updatedAt)
                            }
                        val members =
                            collections.flatMap { collection ->
                                collection.books.mapIndexed { index, book ->
                                    CollectionBookEntity(collection.id, book.id, index)
                                }
                            }
                        collectionDao.replaceAll(entities, members)
                    }
                }
            result
                .onSuccess { connectionStatusRepository.markServerSuccess() }
                .onFailure { connectionStatusRepository.markServerFailure() }
            return result
        }

        suspend fun refresh(id: String): Result<Unit> {
            val result = runCatching { cacheCoordinator.exclusive { cache(client.collection(id)) } }
            result
                .onSuccess { connectionStatusRepository.markServerSuccess() }
                .onFailure { connectionStatusRepository.markServerFailure() }
            return result
        }

        private suspend fun cache(dto: CollectionDto) {
            collectionDao.upsert(
                CollectionEntity(
                    id = dto.id,
                    name = dto.name,
                    description = dto.description,
                    libraryId = dto.libraryId,
                    updatedAt = dto.updatedAt,
                ),
            )

            bookDao.upsertAll(dto.books.map { it.toEntity(bookDao.get(it.id)) })

            val members =
                dto.books.mapIndexed { index, book ->
                    CollectionBookEntity(collectionId = dto.id, bookId = book.id, position = index)
                }
            collectionDao.replaceMembers(dto.id, members)
        }
    }

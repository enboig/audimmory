package org.audimmory.mobile.data.repository

import kotlinx.coroutines.flow.Flow
import org.audimmory.mobile.core.Iso8601
import org.audimmory.mobile.data.local.BookmarkDao
import org.audimmory.mobile.data.local.BookmarkEntity
import org.audimmory.mobile.data.local.ProgressDao
import org.audimmory.mobile.data.remote.BookmarkDto
import org.audimmory.mobile.data.remote.GrimmoryClient
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first bookmarks. Local creates/deletes are applied immediately and
 * [sync] reconciles them with Grimmory.
 *
 * Grimmory assigns bookmark IDs itself, so a bookmark created on the device is
 * stored under a temporary UUID and re-keyed to the server ID once sync has
 * created it. A row whose ID is not numeric has therefore never reached the
 * server: deleting it needs no request, and pushing it means creating it.
 *
 * Grimmory has no deletion feed either. Pulling a book's bookmarks returns the
 * full current list, so synced rows missing from it were deleted elsewhere.
 */
@Singleton
class BookmarkRepository
    @Inject
    constructor(
        private val client: GrimmoryClient,
        private val bookmarkDao: BookmarkDao,
        private val progressDao: ProgressDao,
        private val connectionStatusRepository: ConnectionStatusRepository,
    ) {
        fun observeForBook(bookId: String): Flow<List<BookmarkEntity>> = bookmarkDao.observeForBook(bookId)

        /** Adds a bookmark at [positionSeconds] locally (marked dirty for sync). */
        suspend fun add(
            bookId: String,
            positionSeconds: Double,
            note: String?,
        ) {
            bookmarkDao.upsert(
                BookmarkEntity(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    positionSeconds = positionSeconds.coerceAtLeast(0.0),
                    note = note?.trim()?.ifEmpty { null },
                    updatedAt = Iso8601.now(),
                    dirty = true,
                    deleted = false,
                ),
            )
        }

        /** Tombstones a bookmark locally; the deletion is pushed on next sync. */
        suspend fun delete(id: String) {
            if (isServerId(id)) bookmarkDao.markDeleted(id) else bookmarkDao.hardDelete(id)
        }

        /**
         * Two-way sync: push deletion tombstones and dirty creates/updates, then
         * pull the bookmarks of books with bookmarks or recent progress.
         */
        suspend fun sync(): Result<Unit> {
            val result =
                runCatching {
                    push()
                    val books = (bookmarkDao.bookIds() + progressDao.recentBookIds(PULL_LIMIT)).distinct()
                    for (bookId in books) {
                        pull(bookId)
                    }
                }
            result
                .onSuccess { connectionStatusRepository.markServerSuccess() }
                .onFailure { connectionStatusRepository.markServerFailure() }
            return result
        }

        /**
         * Refreshes a single book's bookmarks from the server into the local cache
         * (used when opening the bookmarks view online).
         */
        suspend fun refreshForBook(bookId: String): Result<Unit> {
            val result = runCatching { pull(bookId) }
            result
                .onSuccess { connectionStatusRepository.markServerSuccess() }
                .onFailure { connectionStatusRepository.markServerFailure() }
            return result
        }

        private suspend fun push() {
            for (tomb in bookmarkDao.tombstones()) {
                if (isServerId(tomb.id)) {
                    // A 404 means it is already gone; anything else is retried next sync.
                    val deleted = runCatching { client.deleteBookmark(tomb.id) }
                    if (deleted.isFailure && deleted.exceptionOrNull().isNotFound().not()) continue
                }
                bookmarkDao.hardDelete(tomb.id)
            }

            for (local in bookmarkDao.dirty()) {
                if (isServerId(local.id)) {
                    client.updateBookmarkNote(local.id, local.positionSeconds, local.note)
                    bookmarkDao.clearDirty(local.id)
                } else {
                    val created = client.createBookmark(local.bookId, local.positionSeconds, local.note)
                    // Keep the local position: the server copy went through a
                    // millisecond and track conversion and may differ by rounding.
                    bookmarkDao.rekey(local.id, created.toEntity().copy(positionSeconds = local.positionSeconds, note = local.note))
                }
            }
        }

        private suspend fun pull(bookId: String) {
            val remote = client.bookmarksForBook(bookId)
            val remoteIds = remote.map { it.id }.toSet()
            for (dto in remote) {
                val current = bookmarkDao.get(dto.id)
                // Leave records with pending local changes; they are pushed next sync.
                if (current?.dirty == true || current?.deleted == true) continue
                bookmarkDao.upsert(dto.toEntity())
            }
            for (local in bookmarkDao.allForBook(bookId)) {
                if (isServerId(local.id) && local.id !in remoteIds && !local.dirty && !local.deleted) {
                    bookmarkDao.hardDelete(local.id)
                }
            }
        }

        private fun BookmarkDto.toEntity() =
            BookmarkEntity(
                id = id,
                bookId = bookId,
                positionSeconds = positionSeconds,
                note = note,
                updatedAt = updatedAt,
                dirty = false,
                deleted = false,
            )

        private companion object {
            const val PULL_LIMIT = 25

            fun isServerId(id: String): Boolean = id.isNotEmpty() && id.all(Char::isDigit)

            fun Throwable?.isNotFound(): Boolean = (this as? retrofit2.HttpException)?.code() == 404
        }
    }

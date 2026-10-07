package org.audimmory.mobile.data.repository

import org.audimmory.mobile.data.local.BookDao
import org.audimmory.mobile.data.local.ProgressDao
import org.audimmory.mobile.data.local.SessionStore
import org.audimmory.mobile.data.remote.BookSummaryDto
import org.audimmory.mobile.data.remote.GrimmoryClient
import org.audimmory.mobile.data.remote.bookCoverUrl
import javax.inject.Inject
import javax.inject.Singleton

/** A book as shown on a home shelf. */
data class ShelfBook(
    val id: String,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val finished: Boolean = false,
    /** 0f..1f listening progress, or null when not started/unknown. */
    val progressFraction: Float? = null,
)

/** The home page's shelves. */
data class HomeShelves(
    val continueListening: List<ShelfBook> = emptyList(),
    val recentlyAdded: List<ShelfBook> = emptyList(),
    val listenAgain: List<ShelfBook> = emptyList(),
)

@Singleton
class HomeRepository
    @Inject
    constructor(
        private val client: GrimmoryClient,
        private val sessionStore: SessionStore,
        private val bookDao: BookDao,
        private val progressDao: ProgressDao,
        private val connectionStatusRepository: ConnectionStatusRepository,
    ) {
        suspend fun load(): Result<HomeShelves> {
            val result =
                runCatching {
                    val baseUrl = sessionStore.currentServerUrl()
                    val home = client.home()
                    HomeShelves(
                        continueListening = home.continueListening.map { it.toShelfBook(baseUrl) },
                        recentlyAdded = home.recentlyAdded.map { it.toShelfBook(baseUrl) },
                        listenAgain = home.listenAgain.map { it.toShelfBook(baseUrl) },
                    )
                }
            result
                .onSuccess { connectionStatusRepository.markServerSuccess() }
                .onFailure { connectionStatusRepository.markServerFailure() }
            return result
        }

        private suspend fun BookSummaryDto.toShelfBook(baseUrl: String): ShelfBook {
            // Local progress is fresher than the server's while it is unsynced.
            val local = progressDao.get(id)
            val fraction =
                local
                    ?.takeIf { it.durationSeconds > 0 }
                    ?.let { (it.currentSeconds / it.durationSeconds).toFloat().coerceIn(0f, 1f) }
                    ?: progressFraction
            return ShelfBook(
                id = id,
                title = title,
                author = authors.joinToString(", ") { it.name }.ifEmpty { null },
                coverUrl = bookDao.get(id)?.coverModel(baseUrl) ?: if (hasCover) bookCoverUrl(baseUrl, id, audiobookCover) else null,
                finished = local?.finished ?: finished,
                progressFraction = fraction,
            )
        }
    }

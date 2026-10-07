package org.audimmory.mobile.playback

import android.net.Uri
import org.audimmory.mobile.data.local.SessionStore
import org.audimmory.mobile.data.remote.audiobookStreamUrl
import org.audimmory.mobile.data.remote.audiobookTrackUrl
import org.audimmory.mobile.data.repository.DownloadRepository
import org.audimmory.mobile.data.repository.LibraryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Works out where a book's audio comes from: downloaded files when the book is
 * available offline, otherwise the authenticated Grimmory stream URLs. A
 * single-file book (m4b, m4a, mp3, opus) resolves to one source; a folder-based
 * book resolves to one source per track, which [AudiobookMediaSourceFactory]
 * joins into a single timeline.
 */
@Singleton
class BookAudioResolver
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        private val downloadRepository: DownloadRepository,
        private val sessionStore: SessionStore,
    ) {
        suspend fun resolve(bookId: String): List<AudioTrackSource> {
            val localFiles = downloadRepository.localTrackFiles(bookId)
            var tracks = libraryRepository.getTracks(bookId)
            // A book never opened has no cached layout; fetch it when online.
            if (tracks.isEmpty() && localFiles.isEmpty()) {
                libraryRepository.refreshBook(bookId)
                tracks = libraryRepository.getTracks(bookId)
            }
            val folderBased = libraryRepository.getBook(bookId)?.folderBased == true
            val baseUrl = sessionStore.currentServerUrl()

            fun remote(index: Int): Uri =
                Uri.parse(
                    if (folderBased) audiobookTrackUrl(baseUrl, bookId, index) else audiobookStreamUrl(baseUrl, bookId),
                )

            if (tracks.isEmpty()) {
                val local = localFiles[0]
                return listOf(AudioTrackSource(local?.let(Uri::fromFile) ?: remote(0), 0))
            }
            return tracks.sortedBy { it.index }.map { track ->
                val uri = localFiles[track.index]?.let(Uri::fromFile) ?: remote(track.index)
                AudioTrackSource(uri, track.durationMs)
            }
        }
    }

package org.audimmory.mobile.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.audimmory.mobile.data.local.SessionStore
import org.audimmory.mobile.data.local.TrackEntity
import org.audimmory.mobile.data.remote.audiobookStreamUrl
import org.audimmory.mobile.data.remote.audiobookTrackUrl
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Progress of an in-flight download. */
sealed interface DownloadProgress {
    data class Running(
        val bytesRead: Long,
        val totalBytes: Long?,
    ) : DownloadProgress {
        // Server-reported sizes can be slightly off (e.g. metadata rewritten after scanning).
        val fraction: Float? = totalBytes?.takeIf { it > 0 }?.let { (bytesRead.toFloat() / it).coerceAtMost(1f) }
    }

    data class Completed(
        val dir: File,
        val bytes: Long,
    ) : DownloadProgress

    data class Failed(
        val error: Throwable,
    ) : DownloadProgress
}

/**
 * Streams a book's audio from Grimmory to app-private storage.
 *
 * Every book gets its own directory holding one file per track, named
 * `<trackIndex>.<extension>`: a single-file book (m4b, m4a, mp3, opus) has
 * just `0.m4b` or similar, a folder-based book has `0.mp3`, `1.mp3`, ….
 * Tracks already on disk at their expected size are skipped, so a retried
 * download resumes at the first missing track.
 *
 * Auth and base-URL rewriting are handled by the shared OkHttp interceptors.
 * Cancellation deletes only the partial file of the track in progress.
 */
@Singleton
class AudioDownloader
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val client: OkHttpClient,
        private val sessionStore: SessionStore,
    ) {
        private val downloadsDir: File
            get() = File(context.filesDir, AUDIO_DIR_NAME).apply { mkdirs() }

        fun dirFor(bookId: String): File = File(downloadsDir, bookId)

        /** Downloaded track files by track index; partial files are excluded. */
        fun trackFiles(bookId: String): Map<Int, File> =
            dirFor(bookId)
                .listFiles()
                .orEmpty()
                .filter { it.isFile && !it.name.endsWith(PART_SUFFIX) }
                .mapNotNull { file ->
                    file.name
                        .substringBefore('.')
                        .toIntOrNull()
                        ?.let { it to file }
                }.toMap()

        fun download(
            bookId: String,
            folderBased: Boolean,
            tracks: List<TrackEntity>,
        ): Flow<DownloadProgress> =
            flow {
                val baseUrl = sessionStore.currentServerUrl()
                val dir = dirFor(bookId).apply { mkdirs() }
                val ordered = tracks.sortedBy { it.index }.ifEmpty { listOf(TrackEntity(bookId, 0, null, null, 0, 0, null)) }
                val total = ordered.sumOf { it.sizeBytes ?: 0 }.takeIf { size -> ordered.all { it.sizeBytes != null } && size > 0 }
                var readTotal = 0L
                var partial: File? = null

                try {
                    for (track in ordered) {
                        val target = File(dir, "${track.index}.${extensionFor(track.fileName)}")
                        if (target.exists() && track.sizeBytes != null && target.length() == track.sizeBytes) {
                            readTotal += target.length()
                            continue
                        }
                        val url = if (folderBased) audiobookTrackUrl(baseUrl, bookId, track.index) else audiobookStreamUrl(baseUrl, bookId)
                        val tmp = File(target.absolutePath + PART_SUFFIX).also { partial = it }

                        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                            (response.body ?: throw IOException("empty body")).byteStream().use { input ->
                                tmp.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        coroutineContext.ensureActive()
                                        val read = input.read(buffer)
                                        if (read == -1) break
                                        output.write(buffer, 0, read)
                                        readTotal += read
                                        emit(DownloadProgress.Running(readTotal, total))
                                    }
                                }
                            }
                        }

                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            tmp.delete()
                        }
                        partial = null
                    }
                    emit(DownloadProgress.Completed(dir, dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }))
                } catch (t: Throwable) {
                    partial?.delete()
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    emit(DownloadProgress.Failed(t))
                }
            }.flowOn(Dispatchers.IO)

        fun delete(bookId: String) {
            dirFor(bookId).deleteRecursively()
        }

        /**
         * Deletes every downloaded book, including partial downloads.
         *
         * For account teardown, where the Room rows naming these files are being
         * cleared in the same operation. Unlike [delete] this takes no lock and
         * touches no database, so it is safe to call from inside an existing
         * [org.audimmory.mobile.data.repository.CacheCoordinator] block.
         */
        fun deleteAllFiles(): Int = clearDirectoryContents(downloadsDir)

        /**
         * Dismisses any download notifications left in the shade.
         *
         * Lives here because this class already holds the download subsystem's
         * application [Context], which [AuthRepository][org.audimmory.mobile.data.repository.AuthRepository]
         * deliberately does not.
         */
        fun cancelNotifications() = DownloadNotifications.cancelAll(context)

        private fun extensionFor(fileName: String?): String =
            fileName
                ?.substringAfterLast('.', "")
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isLetterOrDigit) }
                ?: "audio"

        private companion object {
            const val PART_SUFFIX = ".part"
        }
    }

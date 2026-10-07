package org.audimmory.mobile.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local mirror of the Grimmory server's audiobooks, keyed by the server's
 * numeric IDs (as strings) so records reconcile 1:1 during sync. This is the
 * offline-first source of truth the UI reads from.
 */

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String?,
    val authors: String?,
    val narrators: String?,
    val durationSeconds: Double,
    val size: Long?,
    val publishedYear: Int?,
    val publishedDate: String? = null,
    val addedAt: String?,
    val fileModified: String?,
    val libraryId: String?,
    val hasCover: Boolean = false,
    /** True when [hasCover] refers to Grimmory's dedicated audiobook cover. */
    val audiobookCover: Boolean = false,
    val coverLocalPath: String? = null,
    val coverUpdatedAt: String? = null,
    val description: String?,
    val publisher: String?,
    val language: String?,
    /** Cover version reported by the server; changes when the cover does. */
    val updatedAt: String?,
    /** True for a directory of audio files streamed track by track. */
    val folderBased: Boolean = false,
    /** Grimmory's ID of the audio file; known once the detail was fetched. */
    val bookFileId: String? = null,
)

/**
 * One audio file of a book, positioned on the book's continuous timeline.
 * Single-file books have exactly one track starting at 0.
 */
@Entity(
    tableName = "tracks",
    primaryKeys = ["bookId", "index"],
)
data class TrackEntity(
    val bookId: String,
    val index: Int,
    val title: String?,
    val fileName: String?,
    val startMs: Long,
    val durationMs: Long,
    val sizeBytes: Long?,
)

@Entity(
    tableName = "book_facets",
    primaryKeys = ["bookId", "category", "facetId"],
    indices = [Index("bookId"), Index(value = ["category", "facetId"])],
)
data class BookFacetEntity(
    val bookId: String,
    val category: String,
    val facetId: String,
    val name: String,
    val position: Int,
)

@Entity(tableName = "cached_libraries")
data class CachedLibraryEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "chapters",
    indices = [Index("bookId")],
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String?,
    val index: Int,
    val startSeconds: Double,
    val endSeconds: Double,
)

/**
 * Local playback progress. [dirty] marks records with unsynced local writes
 * that the sync worker must push to the server (optimistic offline sync).
 */
@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val bookId: String,
    val currentSeconds: Double,
    val durationSeconds: Double,
    val finished: Boolean,
    // Server-derived, pull/display-only (not pushed back on progress updates).
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val lastPlayedAt: String?,
    val updatedAt: String?,
    val dirty: Boolean = false,
)

/**
 * Tracks a book downloaded for offline playback. [localPath] is the book's
 * download directory, holding one file per track.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val bookId: String,
    val localPath: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val completed: Boolean,
)

/**
 * A bookmark. Once synced, [id] is Grimmory's numeric bookmark ID; a bookmark
 * created offline carries a temporary client UUID until sync creates it on the
 * server and re-keys the row. [dirty] marks an unsynced local create/update to
 * push; [deleted] is a tombstone for a locally-deleted bookmark whose deletion
 * still needs pushing.
 */
@Entity(
    tableName = "bookmarks",
    indices = [Index("bookId")],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val positionSeconds: Double,
    val note: String?,
    val updatedAt: String?,
    val dirty: Boolean = false,
    val deleted: Boolean = false,
)

@Entity(
    tableName = "playback_sessions",
    indices = [Index("bookId")],
)
data class PlaybackSessionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val title: String?,
    val authors: String?,
    val playMethod: String,
    val deviceInfo: String,
    val startedAt: String,
    val updatedAt: String,
    val endedAt: String?,
    val timeListenedSeconds: Long,
    val lastPositionSeconds: Double,
    val durationSeconds: Double,
    val dirty: Boolean = true,
)

@Entity(
    tableName = "playback_events",
    indices = [Index("bookId"), Index("sessionId")],
)
data class PlaybackEventEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val bookId: String,
    val event: String,
    val type: String,
    val positionSeconds: Double,
    val timestamp: String,
    val serverSyncAttempted: Boolean = false,
    val serverSyncSuccess: Boolean? = null,
    val serverSyncMessage: String? = null,
    val dirty: Boolean = true,
)

// ---------------------------------------------------------------------------
// Series, Collections, Playlists (browse-only mirrors of the server). Each has
// an ordered membership join table pointing at cached BookEntity rows.
// ---------------------------------------------------------------------------

@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "series_books",
    primaryKeys = ["seriesId", "bookId"],
    indices = [Index("bookId")],
)
data class SeriesBookEntity(
    val seriesId: String,
    val bookId: String,
    val sequence: String?,
    val position: Int,
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val libraryId: String?,
    val updatedAt: String?,
)

@Entity(
    tableName = "collection_books",
    primaryKeys = ["collectionId", "bookId"],
    indices = [Index("bookId")],
)
data class CollectionBookEntity(
    val collectionId: String,
    val bookId: String,
    val position: Int,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val updatedAt: String?,
)

@Entity(
    tableName = "playlist_books",
    primaryKeys = ["playlistId", "bookId"],
    indices = [Index("bookId")],
)
data class PlaylistBookEntity(
    val playlistId: String,
    val bookId: String,
    val position: Int,
)

package org.audimmory.mobile.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the Grimmory server's JSON API (Spring/Jackson, camelCase).
 * Field names must match the server contract exactly; everything optional is
 * nullable because Grimmory omits null fields (`JsonInclude.NON_NULL`).
 *
 * Repositories never see these: [GrimmoryClient] converts them into the
 * app-level models in `Dtos.kt`.
 */

@Serializable
data class GrimmoryLoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class GrimmoryRefreshRequest(
    val refreshToken: String,
)

@Serializable
data class GrimmoryLogoutRequest(
    val refreshToken: String?,
)

@Serializable
data class GrimmoryTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val expires: Long? = null,
)

@Serializable
data class GrimmoryUser(
    val id: Long,
    val username: String,
    val name: String? = null,
    val email: String? = null,
    val permissions: GrimmoryPermissions? = null,
)

@Serializable
data class GrimmoryPermissions(
    // Lombok's `isAdmin` getter serialises as "admin".
    @SerialName("admin") val isAdmin: Boolean = false,
    val canDownload: Boolean = false,
)

@Serializable
data class GrimmoryVersion(
    val current: String? = null,
)

/**
 * A page of `GET /api/v1/books/page`. Grimmory also returns a cursor and
 * HATEOAS links; plain page numbers are enough here.
 */
@Serializable
data class GrimmoryBrowsePage<T>(
    val content: List<T> = emptyList(),
    val page: GrimmoryPageInfo = GrimmoryPageInfo(),
)

@Serializable
data class GrimmoryPageInfo(
    val number: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

@Serializable
data class GrimmoryLibrary(
    val id: Long,
    val name: String,
)

/**
 * A book as Grimmory's main API returns it, for lists and detail alike. The
 * list endpoint strips a few fields (description, lock flags); progress and
 * read status are the signed-in user's.
 */
@Serializable
data class GrimmoryBook(
    val id: Long,
    val libraryId: Long? = null,
    val title: String? = null,
    val primaryFile: GrimmoryBookFile? = null,
    val alternativeFormats: List<GrimmoryBookFile>? = null,
    val metadata: GrimmoryBookMetadata? = null,
    val addedOn: String? = null,
    val lastReadTime: String? = null,
    val readStatus: String? = null,
    val audiobookProgress: GrimmoryAudiobookProgress? = null,
)

@Serializable
data class GrimmoryBookMetadata(
    val title: String? = null,
    val subtitle: String? = null,
    val authors: List<String>? = null,
    val narrator: String? = null,
    val categories: List<String>? = null,
    val seriesName: String? = null,
    val seriesNumber: Float? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val description: String? = null,
    val isbn13: String? = null,
    val language: String? = null,
    val coverUpdatedOn: String? = null,
    val audiobookCoverUpdatedOn: String? = null,
)

@Serializable
data class GrimmoryBookFile(
    val id: Long,
    val fileName: String? = null,
    val folderBased: Boolean = false,
    val bookType: String? = null,
    val fileSizeKb: Long? = null,
    val extension: String? = null,
)

@Serializable
data class GrimmoryAudiobookProgress(
    val positionMs: Long? = null,
    val trackIndex: Int? = null,
    val percentage: Float? = null,
    val updatedAt: String? = null,
)

@Serializable
data class GrimmoryFileProgress(
    val bookFileId: Long,
    val positionData: String?,
    val positionHref: String?,
    val progressPercent: Float,
)

@Serializable
data class GrimmoryProgressUpdate(
    val bookId: Long,
    val fileProgress: GrimmoryFileProgress? = null,
    val audiobookProgress: GrimmoryAudiobookProgress? = null,
    val dateFinished: String? = null,
)

@Serializable
data class GrimmoryAudiobookInfo(
    val bookId: Long,
    val bookFileId: Long? = null,
    val title: String? = null,
    val author: String? = null,
    val narrator: String? = null,
    val durationMs: Long? = null,
    val totalSizeBytes: Long? = null,
    val folderBased: Boolean = false,
    val chapters: List<GrimmoryChapter>? = null,
    val tracks: List<GrimmoryTrack>? = null,
)

@Serializable
data class GrimmoryChapter(
    val index: Int,
    val title: String? = null,
    val startTimeMs: Long = 0,
    val endTimeMs: Long = 0,
)

@Serializable
data class GrimmoryTrack(
    val index: Int,
    val fileName: String? = null,
    val title: String? = null,
    val durationMs: Long? = null,
    val fileSizeBytes: Long? = null,
    val cumulativeStartMs: Long? = null,
)

@Serializable
data class GrimmoryBookmark(
    val id: Long,
    val bookId: Long,
    val positionMs: Long? = null,
    val trackIndex: Int? = null,
    val title: String? = null,
    val notes: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class GrimmoryCreateBookmark(
    val bookId: Long,
    val positionMs: Long,
    val trackIndex: Int?,
    val title: String,
)

@Serializable
data class GrimmoryUpdateBookmark(
    val title: String? = null,
    val notes: String? = null,
)

@Serializable
data class GrimmoryReadingSession(
    val bookId: Long,
    val bookType: String,
    val startTime: String,
    val endTime: String,
    val durationSeconds: Int,
    val durationFormatted: String?,
    val startProgress: Float?,
    val endProgress: Float?,
    val progressDelta: Float?,
    val startLocation: String?,
    val endLocation: String?,
)

@Serializable
data class GrimmoryShelf(
    val id: Long,
    val name: String,
    val bookCount: Int = 0,
)

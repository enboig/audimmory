package org.audimmory.mobile.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the subset of the Grimmory REST API the app uses.
 * Raw endpoints only; [GrimmoryClient] layers the audiobook semantics on top.
 */
interface GrimmoryApi {
    @POST("api/v1/auth/login")
    suspend fun login(
        @Body body: GrimmoryLoginRequest,
    ): GrimmoryTokens

    @POST("api/v1/auth/logout")
    suspend fun logout(
        @Body body: GrimmoryLogoutRequest,
    )

    @GET("api/v1/users/me")
    suspend fun me(): GrimmoryUser

    @GET("api/v1/version")
    suspend fun version(): GrimmoryVersion

    @GET("api/v1/libraries")
    suspend fun libraries(): List<GrimmoryLibrary>

    @GET("api/v1/app/books")
    suspend fun books(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("fileType") fileType: String = AUDIOBOOK,
        @Query("sort") sort: String? = null,
        @Query("dir") dir: String? = null,
        @Query("shelfId") shelfId: Long? = null,
        @Query("magicShelfId") magicShelfId: Long? = null,
        @Query("status") status: List<String>? = null,
    ): GrimmoryPage<GrimmoryBookSummary>

    @GET("api/v1/app/books/{id}")
    suspend fun book(
        @Path("id") id: Long,
    ): GrimmoryBookDetail

    @GET("api/v1/app/books/{id}/progress")
    suspend fun progress(
        @Path("id") id: Long,
    ): GrimmoryProgressResponse

    @PUT("api/v1/app/books/{id}/progress")
    suspend fun updateProgress(
        @Path("id") id: Long,
        @Body body: GrimmoryProgressUpdate,
    )

    @GET("api/v1/audiobooks/{id}/info")
    suspend fun audiobookInfo(
        @Path("id") id: Long,
        @Query("bookType") bookType: String = AUDIOBOOK,
    ): GrimmoryAudiobookInfo

    @GET("api/v1/app/series")
    suspend fun series(
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): GrimmoryPage<GrimmorySeriesSummary>

    @GET("api/v1/app/series/{name}/books")
    suspend fun seriesBooks(
        @Path("name") name: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): GrimmoryPage<GrimmoryBookSummary>

    @GET("api/v1/app/shelves")
    suspend fun shelves(): List<GrimmoryShelf>

    @GET("api/v1/app/shelves/magic")
    suspend fun magicShelves(): List<GrimmoryShelf>

    @GET("api/v1/app/shelves/magic/{id}/books")
    suspend fun magicShelfBooks(
        @Path("id") id: Long,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): GrimmoryPage<GrimmoryBookSummary>

    @GET("api/v1/bookmarks/book/{bookId}")
    suspend fun bookmarksForBook(
        @Path("bookId") bookId: Long,
    ): List<GrimmoryBookmark>

    @POST("api/v1/bookmarks")
    suspend fun createBookmark(
        @Body body: GrimmoryCreateBookmark,
    ): GrimmoryBookmark

    @PUT("api/v1/bookmarks/{id}")
    suspend fun updateBookmark(
        @Path("id") id: Long,
        @Body body: GrimmoryUpdateBookmark,
    ): GrimmoryBookmark

    @DELETE("api/v1/bookmarks/{id}")
    suspend fun deleteBookmark(
        @Path("id") id: Long,
    )

    @POST("api/v1/reading-sessions")
    suspend fun recordReadingSession(
        @Body body: GrimmoryReadingSession,
    )

    companion object {
        const val AUDIOBOOK = "AUDIOBOOK"

        /** Grimmory caps app page sizes at 50. */
        const val PAGE_SIZE = 50
    }
}

private fun base(baseUrl: String) = baseUrl.trimEnd('/')

/** Stream URL for a single-file audiobook (m4b, m4a, mp3, opus). */
fun audiobookStreamUrl(
    baseUrl: String,
    bookId: String,
): String = base(baseUrl) + "/api/v1/audiobooks/$bookId/stream?bookType=${GrimmoryApi.AUDIOBOOK}"

/** Stream URL for one track of a folder-based audiobook (0-indexed). */
fun audiobookTrackUrl(
    baseUrl: String,
    bookId: String,
    trackIndex: Int,
): String = base(baseUrl) + "/api/v1/audiobooks/$bookId/track/$trackIndex/stream?bookType=${GrimmoryApi.AUDIOBOOK}"

/*
 * `bookType=AUDIOBOOK` matters for books that also have an ebook file: without
 * it Grimmory resolves the book's primary file, which may not be audio.
 */

/**
 * URL for a book's cover image (authenticated via the OkHttp interceptor).
 * Grimmory keeps a separate square cover for audiobooks; [audiobookCover]
 * picks it when the server reported one.
 */
fun bookCoverUrl(
    baseUrl: String,
    bookId: String,
    audiobookCover: Boolean,
): String = base(baseUrl) + "/api/v1/media/book/$bookId/" + if (audiobookCover) "audiobook-cover" else "cover"

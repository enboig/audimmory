package org.audimmory.mobile.data.repository

import android.net.Uri
import org.audimmory.mobile.data.local.BookEntity
import org.audimmory.mobile.data.local.MemberCoverRow
import org.audimmory.mobile.data.remote.bookCoverUrl
import java.io.File

/** Coil model for a book cover: local file first, authenticated server URL second. */
fun BookEntity.coverModel(serverUrl: String): String? {
    val local =
        coverLocalPath
            ?.takeIf { hasCover && coverUpdatedAt == updatedAt && File(it).exists() }
            ?.let { Uri.fromFile(File(it)).toString() }

    return local ?: if (hasCover) bookCoverUrl(serverUrl, id, audiobookCover) else null
}

/** Same local-first cover resolution for a lightweight membership preview row. */
fun MemberCoverRow.coverModel(serverUrl: String): String? {
    val local =
        coverLocalPath
            ?.takeIf { hasCover && coverUpdatedAt == updatedAt && File(it).exists() }
            ?.let { Uri.fromFile(File(it)).toString() }

    return local ?: if (hasCover) bookCoverUrl(serverUrl, bookId, audiobookCover) else null
}

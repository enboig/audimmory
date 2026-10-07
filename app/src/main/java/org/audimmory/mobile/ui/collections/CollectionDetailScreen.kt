package org.audimmory.mobile.ui.collections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.audimmory.mobile.ui.components.BookGridDetail
import org.audimmory.mobile.ui.components.DetailBook

@Composable
fun CollectionDetailScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    val collection by viewModel.collection.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()

    BookGridDetail(
        title = collection?.name ?: "Shelf",
        emptyText = "This collection is empty.",
        books = books.map { DetailBook(it.id, it.title, it.author, it.coverUrl) },
        onBack = onBack,
        onOpenBook = onOpenBook,
    )
}

package org.audimmory.mobile.ui.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.audimmory.mobile.R
import org.audimmory.mobile.ui.components.BookGridDetail
import org.audimmory.mobile.ui.components.DetailBook

@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onOpenBook: (String) -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val series by viewModel.series.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()

    BookGridDetail(
        title = series?.name ?: stringResource(R.string.nav_series),
        emptyText = stringResource(R.string.series_detail_empty),
        books = books.map { DetailBook(it.id, it.title, it.author, it.coverUrl, it.sequence) },
        onBack = onBack,
        onOpenBook = onOpenBook,
    )
}

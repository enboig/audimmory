package org.audimmory.mobile.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.audimmory.mobile.R
import org.audimmory.mobile.data.repository.ShelfBook
import org.audimmory.mobile.ui.components.AudimmoryRefreshIndicator
import org.audimmory.mobile.ui.components.BookCard
import org.audimmory.mobile.ui.components.ConnectionStatusIcon
import org.audimmory.mobile.ui.components.TopTab
import org.audimmory.mobile.ui.components.TopTabs
import org.audimmory.mobile.ui.theme.JetBrainsMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenBook: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onSelectTab: (TopTab) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val localBooks by viewModel.localBooks.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(R.drawable.ic_brand),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.app_name),
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(8.dp))
                            ConnectionStatusIcon()
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.action_menu))
                        }
                    },
                )
                TopTabs(selected = TopTab.HOME, onSelect = onSelectTab)
            }
        },
    ) { padding ->
        val shelves = state.shelves
        val nothing =
            shelves.continueListening.isEmpty() &&
                shelves.recentlyAdded.isEmpty() &&
                shelves.listenAgain.isEmpty() &&
                localBooks.isEmpty()

        val refreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { viewModel.refresh() },
            state = refreshState,
            modifier = Modifier.fillMaxSize().padding(padding),
            indicator = {
                AudimmoryRefreshIndicator(
                    state = refreshState,
                    isRefreshing = state.loading,
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp),
                )
            },
        ) {
            when {
                nothing && state.loading ->
                    CenterText(stringResource(R.string.home_loading))

                nothing && state.error != null ->
                    CenterText(stringResource(R.string.error_server_unreachable, state.error.orEmpty()))

                nothing ->
                    CenterText(stringResource(R.string.home_empty))

                else -> {
                    val continueListening = stringResource(R.string.home_continue_listening)
                    val recentlyAdded = stringResource(R.string.home_recently_added)
                    val listenAgain = stringResource(R.string.home_listen_again)
                    val downloaded = stringResource(R.string.home_local_books)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        shelfItem(continueListening, shelves.continueListening, onOpenBook)
                        shelfItem(recentlyAdded, shelves.recentlyAdded, onOpenBook)
                        shelfItem(listenAgain, shelves.listenAgain, onOpenBook)
                        shelfItem(downloaded, localBooks, onOpenBook)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shelfItem(
    title: String,
    books: List<ShelfBook>,
    onOpenBook: (String) -> Unit,
) {
    if (books.isEmpty()) return
    item(key = title) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(books, key = { it.id }) { book ->
                BookCard(
                    title = book.title,
                    author = book.author,
                    coverUrl = book.coverUrl,
                    finished = book.finished,
                    progressFraction = book.progressFraction,
                    onClick = { onOpenBook(book.id) },
                )
            }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

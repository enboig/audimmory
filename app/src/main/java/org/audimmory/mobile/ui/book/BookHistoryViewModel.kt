package org.audimmory.mobile.ui.book

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.audimmory.mobile.core.DateTimeFormat
import org.audimmory.mobile.data.local.BookEntity
import org.audimmory.mobile.data.local.PlaybackEventEntity
import org.audimmory.mobile.data.repository.AuthRepository
import org.audimmory.mobile.data.repository.LibraryRepository
import org.audimmory.mobile.data.repository.PlaybackHistoryRepository
import javax.inject.Inject

@HiltViewModel
class BookHistoryViewModel
    @Inject
    constructor(
        libraryRepository: LibraryRepository,
        playbackHistoryRepository: PlaybackHistoryRepository,
        authRepository: AuthRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val bookId: String = checkNotNull(savedStateHandle["bookId"])

        val book: StateFlow<BookEntity?> =
            libraryRepository
                .observeBook(bookId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        val events: StateFlow<List<PlaybackEventEntity>> =
            playbackHistoryRepository
                .observeEventsForBook(bookId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val dateFormat: StateFlow<String> =
            authRepository.dateFormat
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    DateTimeFormat.DEFAULT_DATE_FORMAT,
                )

        val timeFormat: StateFlow<String> =
            authRepository.timeFormat
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    DateTimeFormat.DEFAULT_TIME_FORMAT,
                )
    }

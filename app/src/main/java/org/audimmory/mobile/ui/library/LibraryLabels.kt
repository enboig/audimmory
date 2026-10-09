package org.audimmory.mobile.ui.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.audimmory.mobile.R

/*
 * Localized display names for the Library's filter categories, progress states
 * and sort fields. The enums keep their English `label` so the filter, search
 * and sort engines stay free of Android resources; the UI shows these instead.
 */

@StringRes
fun LibraryFilterCategory.labelRes(): Int =
    when (this) {
        LibraryFilterCategory.AUTHORS -> R.string.category_authors
        LibraryFilterCategory.NARRATORS -> R.string.category_narrators
        LibraryFilterCategory.PROGRESS -> R.string.category_progress
        LibraryFilterCategory.SERIES -> R.string.category_series
        LibraryFilterCategory.COLLECTIONS -> R.string.category_shelves
        LibraryFilterCategory.PLAYLISTS -> R.string.category_magic_shelves
        LibraryFilterCategory.GENRES -> R.string.category_genres
        LibraryFilterCategory.PUBLISHERS -> R.string.category_publishers
        LibraryFilterCategory.LANGUAGES -> R.string.category_languages
        LibraryFilterCategory.LIBRARIES -> R.string.category_libraries
    }

@StringRes
fun LibraryProgressState.labelRes(): Int =
    when (this) {
        LibraryProgressState.NOT_STARTED -> R.string.progress_not_started
        LibraryProgressState.IN_PROGRESS -> R.string.progress_in_progress
        LibraryProgressState.FINISHED -> R.string.progress_finished
    }

@StringRes
fun LibrarySort.labelRes(): Int =
    when (this) {
        LibrarySort.TITLE -> R.string.sort_field_title
        LibrarySort.AUTHOR_FIRST -> R.string.sort_field_author_first
        LibrarySort.AUTHOR_LAST -> R.string.sort_field_author_last
        LibrarySort.PUBLISHED -> R.string.sort_field_published
        LibrarySort.ADDED -> R.string.sort_field_added
        LibrarySort.SIZE -> R.string.sort_field_size
        LibrarySort.DURATION -> R.string.sort_field_duration
        LibrarySort.MODIFIED -> R.string.sort_field_modified
        LibrarySort.PROGRESS_UPDATED -> R.string.sort_field_progress_updated
        LibrarySort.PROGRESS_STARTED -> R.string.sort_field_progress_started
        LibrarySort.PROGRESS_FINISHED -> R.string.sort_field_progress_finished
        LibrarySort.RANDOM -> R.string.sort_field_random
    }

@Composable
fun LibraryFilterCategory.displayName(): String = stringResource(labelRes())

@Composable
fun LibrarySort.displayName(): String = stringResource(labelRes())

/**
 * Progress options are built in the ViewModel with English names; every other
 * category's options are server data (author names, genres…) shown as-is.
 */
@Composable
fun progressOptionNames(): Map<String, String> = LibraryProgressState.entries.associate { it.id to stringResource(it.labelRes()) }

package org.audimmory.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.audimmory.mobile.R
import org.audimmory.mobile.data.local.ChapterEntity

/** The chapter's own title, or a localized "Chapter N" when it has none. */
@Composable
fun ChapterEntity.displayTitle(): String = title ?: stringResource(R.string.chapter_number, index + 1)

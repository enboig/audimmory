package org.audimmory.mobile.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory

/** One playable audio file of a book. */
data class AudioTrackSource(
    val uri: Uri,
    val durationMs: Long,
)

/**
 * Plays a multi-file audiobook as **one** `MediaItem`.
 *
 * Grimmory serves folder-based audiobooks (typically a directory of `.mp3`
 * files) track by track. The rest of the app — progress, chapters, jump
 * controls, the headset handling in [PlaybackService] — assumes a book is a
 * single `MediaItem` with one continuous timeline. So rather than queueing the
 * tracks as a playlist, the track list travels in the item's metadata extras
 * and this factory joins them with [ConcatenatingMediaSource2], which exposes
 * a single window spanning every track. Seeking across track boundaries then
 * works like seeking inside one file.
 *
 * Single-file books (m4b, m4a, mp3, opus) carry no track list and fall
 * through to the default factory.
 */
@UnstableApi
class AudiobookMediaSourceFactory(
    dataSourceFactory: DataSource.Factory,
) : MediaSource.Factory {
    private val delegate = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory())

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        delegate.setDrmSessionManagerProvider(provider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        delegate.setLoadErrorHandlingPolicy(policy)
        return this
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val tracks =
            mediaItem.mediaMetadata.extras
                ?.let(::readTracks)
                .orEmpty()
        if (tracks.size <= 1) return delegate.createMediaSource(mediaItem)

        val builder =
            ConcatenatingMediaSource2
                .Builder()
                .setMediaSourceFactory(delegate)
                .setMediaItem(mediaItem)
        tracks.forEach { track ->
            builder.add(MediaItem.fromUri(track.uri), track.durationMs.takeIf { it > 0 } ?: C.TIME_UNSET)
        }
        return builder.build()
    }

    companion object {
        private const val EXTRA_TRACK_URIS = "org.audimmory.mobile.TRACK_URIS"
        private const val EXTRA_TRACK_DURATIONS = "org.audimmory.mobile.TRACK_DURATIONS"

        /** Stores [tracks] in [extras] for [createMediaSource] to pick up. */
        fun writeTracks(
            extras: Bundle,
            tracks: List<AudioTrackSource>,
        ) {
            extras.putStringArrayList(EXTRA_TRACK_URIS, ArrayList(tracks.map { it.uri.toString() }))
            extras.putLongArray(EXTRA_TRACK_DURATIONS, tracks.map { it.durationMs }.toLongArray())
        }

        fun readTracks(extras: Bundle): List<AudioTrackSource> {
            val uris = extras.getStringArrayList(EXTRA_TRACK_URIS) ?: return emptyList()
            val durations = extras.getLongArray(EXTRA_TRACK_DURATIONS) ?: LongArray(uris.size)
            return uris.mapIndexed { i, uri -> AudioTrackSource(Uri.parse(uri), durations.getOrElse(i) { 0L }) }
        }

        /**
         * MP3 files often lack a Xing/VBRI seek table. Without one ExoPlayer
         * refuses to seek at all; constant-bitrate seeking estimates the byte
         * offset instead, which is accurate for CBR and close for VBR — far
         * better for long audiobooks than an unseekable file.
         */
        fun extractorsFactory(): DefaultExtractorsFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
    }
}

package org.audimmory.mobile.playback

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** The jump amounts the user configured in Settings, in milliseconds. */
data class JumpAmounts(
    val backwardMs: Long,
    val forwardMs: Long,
)

/**
 * Wraps the playback [Player] so external transport controls behave like an
 * audiobook player rather than a music player.
 *
 * A book is loaded as a *single* [androidx.media3.common.MediaItem] (chapters
 * are only UI-side arithmetic over the one item), which makes Media3's stock
 * previous/next actively harmful: `KEYCODE_MEDIA_PREVIOUS` from Bluetooth
 * headphones, the notification or the lock screen reaches
 * `Player.seekToPrevious()`, which — finding no previous item — falls through to
 * "seek to position 0" and restarts the whole book.
 *
 * So this player **withholds** the previous/next commands, and jumping is
 * offered instead through [JumpControls]' custom session commands plus the
 * media-button interception in `PlaybackService`. Withholding them is what
 * keeps `ACTION_SKIP_TO_PREVIOUS` / `ACTION_SKIP_TO_NEXT` out of the published
 * `PlaybackState`, which is the only way to stop Android's system media panel
 * from drawing its own fixed track-skip icons. It also means that if a jump
 * ever fails to be intercepted the fallback is "do nothing" rather than
 * "restart the book".
 *
 * The seek-back / seek-forward increments are still reported from the user's
 * settings, so controllers that send `KEYCODE_MEDIA_REWIND` /
 * `KEYCODE_MEDIA_FAST_FORWARD` (car head units, some headsets) agree with the
 * in-app skip buttons instead of using Media3's 5 s / 15 s defaults.
 *
 * Absolute seeks ([Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM]) are untouched, so
 * the in-app player, the Now Playing scrubber and the chapter previous/next
 * buttons all keep their existing behaviour.
 *
 * Implemented on [ForwardingSimpleBasePlayer] rather than `ForwardingPlayer` on
 * purpose. Changing the command set with a plain `ForwardingPlayer` does not
 * stick: `MediaSessionImpl` takes the command set from the
 * `onAvailableCommandsChanged` *event*, and `ForwardingPlayer` re-emits the
 * delegate's unmodified set, so the change would be silently reverted on every
 * timeline change. [ForwardingSimpleBasePlayer] derives and diffs a complete
 * [State], keeping the advertised commands and the emitted events consistent.
 */
@OptIn(markerClass = [UnstableApi::class])
class AudiobookTransportPlayer(
    private val delegate: Player,
    private val jumpAmounts: () -> JumpAmounts,
) : ForwardingSimpleBasePlayer(delegate) {
    override fun getState(): State {
        val state = super.getState()
        val amounts = jumpAmounts()
        return state
            .buildUpon()
            .setAvailableCommands(withoutTrackSkipCommands(state.availableCommands))
            .setSeekBackIncrementMs(amounts.backwardMs)
            .setSeekForwardIncrementMs(amounts.forwardMs)
            .build()
    }

    private fun withoutTrackSkipCommands(available: Player.Commands): Player.Commands =
        Player.Commands
            .Builder()
            .addAll(available)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .remove(Player.COMMAND_SEEK_TO_NEXT)
            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> =
        when (seekCommand) {
            // BasePlayer already resolved these to an absolute, clamped target
            // using the increments reported by getState(), so honour it rather
            // than re-reading a position that may have advanced since. Forwarding
            // to the delegate's own seekBack()/seekForward() would silently use
            // its default increments instead of the user's.
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            -> {
                delegate.seekTo(positionMs)
                // An already-completed future lets SimpleBasePlayer skip its
                // optimistic placeholder state, avoiding a transient wrong
                // position being published to controllers.
                Futures.immediateVoidFuture()
            }

            else -> super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
}

package org.audimmory.mobile.playback

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import org.audimmory.mobile.R

/**
 * The jump-backward / jump-forward transport controls, expressed as **custom**
 * session commands.
 *
 * They have to be custom commands rather than the stock previous/next player
 * commands because of how Android renders media controls. Since Android 13 the
 * notification shade and lock screen do not draw our notification's actions —
 * they build their own panel from the session's `PlaybackState`. Standard
 * actions such as `ACTION_SKIP_TO_PREVIOUS` are drawn with fixed system
 * track-skip icons that an app cannot override; only
 * `PlaybackStateCompat.CustomAction`s carry an app-supplied icon. Media3's
 * `PlayerWrapper.createPlaybackStateCompat` only turns media button preferences
 * into custom actions when the button holds a [SessionCommand] with
 * [SessionCommand.COMMAND_CODE_CUSTOM], so player-command buttons are silently
 * dropped from the panel.
 *
 * [AudiobookTransportPlayer] correspondingly withholds the previous/next player
 * commands, which keeps `ACTION_SKIP_TO_PREVIOUS` / `ACTION_SKIP_TO_NEXT` out of
 * the published `PlaybackState` so the system stops drawing track-skip buttons
 * next to these.
 */
@OptIn(markerClass = [UnstableApi::class])
object JumpControls {
    const val ACTION_JUMP_BACKWARD = "org.audimmory.mobile.JUMP_BACKWARD"
    const val ACTION_JUMP_FORWARD = "org.audimmory.mobile.JUMP_FORWARD"

    /**
     * Must be granted to every controller in
     * `MediaSession.Callback.onConnect`, otherwise Media3 marks the buttons
     * disabled and drops them from both the panel and the notification.
     */
    val sessionCommands: List<SessionCommand> =
        listOf(
            SessionCommand(ACTION_JUMP_BACKWARD, Bundle.EMPTY),
            SessionCommand(ACTION_JUMP_FORWARD, Bundle.EMPTY),
        )

    /** The buttons to publish, in notification order: backward then forward. */
    fun buttons(
        context: Context,
        backwardSeconds: Int,
        forwardSeconds: Int,
    ): ImmutableList<CommandButton> =
        ImmutableList.of(
            CommandButton
                .Builder(backwardIcon(backwardSeconds))
                .setSessionCommand(SessionCommand(ACTION_JUMP_BACKWARD, Bundle.EMPTY))
                .setDisplayName(
                    context.resources.getQuantityString(
                        R.plurals.notification_action_jump_backward,
                        backwardSeconds,
                        backwardSeconds,
                    ),
                ).build(),
            CommandButton
                .Builder(forwardIcon(forwardSeconds))
                .setSessionCommand(SessionCommand(ACTION_JUMP_FORWARD, Bundle.EMPTY))
                .setDisplayName(
                    context.resources.getQuantityString(
                        R.plurals.notification_action_jump_forward,
                        forwardSeconds,
                        forwardSeconds,
                    ),
                ).build(),
        )

    /**
     * Media3 only bundles numbered skip icons for 5/10/15/30 seconds; the
     * remaining values the Settings screen offers (20/45/60) fall back to the
     * unnumbered arrow.
     */
    private fun backwardIcon(seconds: Int): Int =
        when (seconds) {
            5 -> CommandButton.ICON_SKIP_BACK_5
            10 -> CommandButton.ICON_SKIP_BACK_10
            15 -> CommandButton.ICON_SKIP_BACK_15
            30 -> CommandButton.ICON_SKIP_BACK_30
            else -> CommandButton.ICON_SKIP_BACK
        }

    private fun forwardIcon(seconds: Int): Int =
        when (seconds) {
            5 -> CommandButton.ICON_SKIP_FORWARD_5
            10 -> CommandButton.ICON_SKIP_FORWARD_10
            15 -> CommandButton.ICON_SKIP_FORWARD_15
            30 -> CommandButton.ICON_SKIP_FORWARD_30
            else -> CommandButton.ICON_SKIP_FORWARD
        }
}
